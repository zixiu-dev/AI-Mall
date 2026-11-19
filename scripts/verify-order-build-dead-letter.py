#!/usr/bin/env python3
"""用隔离 Broker、MySQL 和 Redis 验证死信告警及按需验证主动恢复。"""
import argparse
import datetime
import json
from pathlib import Path
import re
import subprocess
import time
from urllib.request import urlopen
import uuid

from benchmark_environment import BenchmarkEnvironment

ROOT = Path(__file__).resolve().parents[1]


def wait_for(check, description, seconds=55):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        result = check()
        if result:
            return result
        time.sleep(0.25)
    raise AssertionError(f'等待超时：{description}')


def scrape(port, path):
    with urlopen(f'http://127.0.0.1:{port}{path}', timeout=3) as response:
        return response.read().decode()


def admin(env, *arguments):
    return subprocess.check_output([
        'java', '-Duser.home=' + str(env.folder / 'mq-home'), '-Xmx128m',
        '-cp', str(env.rocketmq / 'lib' / '*'),
        'org.apache.rocketmq.tools.command.MQAdminStartup',
        arguments[0], '-n', f'127.0.0.1:{env.nameserver_port}', *arguments[1:],
    ], text=True, stderr=subprocess.STDOUT, timeout=20)


def start_prometheus(env, binary, metrics_port, http_port, build_group):
    monitoring = ROOT / 'deploy/monitoring'
    config = (monitoring / 'prometheus.yml').read_text()
    config = config.replace('127.0.0.1:9080', f'127.0.0.1:{env.management_port}')
    config = config.replace('127.0.0.1:5557', f'127.0.0.1:{metrics_port}')
    rules = (monitoring / 'order-alerts.yml').read_text()
    (env.folder / 'prometheus.yml').write_text(config)
    (env.folder / 'order-alerts.yml').write_text(rules.replace('wowo_mall_order_build_dev', build_group))
    server = env.launch([
        str(binary.resolve()), '--config.file=' + str(env.folder / 'prometheus.yml'),
        '--storage.tsdb.path=' + str(env.folder / 'prom-data'),
        f'--web.listen-address=127.0.0.1:{http_port}',
    ], 'prometheus')
    env.wait_port(http_port, server)


def submit(env, sku):
    purchase = {'source': 'DIRECT', 'skuId': sku, 'quantity': 2, 'addressId': 20001}
    purchase['expectedPayAmount'] = env.api('/api/store/checkout/quote', body=purchase)['payAmount']
    trace = env.api('/api/store/order-tokens', body={})['traceId']
    accepted = env.api('/api/store/orders/submit', body={'traceId': trace, 'purchase': purchase})
    assert accepted['status'] == 'PROCESSING', accepted
    return trace


def verify_dead_letter_recovery(env, app_options, sku, read_dead_letter_count):
    # 先让建单事务真实失败并保留预占。暂停定时扫描，待真实死信产生后再打开死信订阅。
    options = {**app_options, 'app.order-processing.recovery-interval-ms': 3600000}
    env.start_app('ASYNC', overrides={**options, 'app.order-messaging.build-dead-letter-enabled': 'false'})
    env.sql("CREATE TRIGGER test_fail_build BEFORE INSERT ON customer_order FOR EACH ROW "
            "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='isolated build failure'")
    trace = submit(env, sku)
    wait_for(lambda: read_dead_letter_count() == 2, '有效建单消息真实进入死信', 110)
    env.stop(env.app)
    env.sql("DROP TRIGGER test_fail_build")
    assert env.sql(
        f"SELECT CONCAT(status,',',reserve_status) FROM order_submit_trace WHERE trace_id='{trace}'"
    ) == 'PROCESSING,RESERVED'
    redis_key = f'peak:{env.database}:inventory:v1:{{{sku}}}:available'

    def available():
        return subprocess.check_output(
            ['redis-cli', '-p', str(env.stock_port), '--raw', 'GET', redis_key], text=True
        ).strip()

    assert available() == '6'
    # 置于未来的扫描时间让本次库存释放仅能由死信消费者触发。
    env.sql("UPDATE order_submit_trace SET processing_expires_at=NOW(6)-INTERVAL 1 SECOND,"
            f"next_check_at=NOW(6)+INTERVAL 1 HOUR WHERE trace_id='{trace}'")
    env.start_app('ASYNC', overrides={**options, 'app.order-messaging.build-dead-letter-enabled': 'true'})
    log = env.folder / 'application.log'
    wait_for(lambda: '异步建单死信核对完成，traceId=' + trace in log.read_text(errors='replace'),
             '独立消费者处理有效建单死信')
    assert env.sql(
        "SELECT CONCAT(status,',',reserve_status,',',failure_code) FROM order_submit_trace "
        f"WHERE trace_id='{trace}'"
    ) == 'FAILED,RELEASED,QUEUE_TIMEOUT'
    assert env.sql(f"SELECT COUNT(*) FROM customer_order WHERE submit_trace_id='{trace}'") == '0'
    assert env.sql(f"SELECT CONCAT(stock,',',locked_stock) FROM product_sku WHERE id={sku}") == '8,2'
    assert available() == '8'
    return {'deadLetterCount': read_dead_letter_count(), 'purchase': 'FAILED,RELEASED,QUEUE_TIMEOUT',
            'availableBefore': 6, 'availableAfter': 8, 'recoveryScanBypassedForTest': True}


def verify(env, metrics_port, prometheus_bin, prometheus_port, consume_dead_letters):
    env.start({
        'metricsExporterType': 'PROM',
        'metricsPromExporterHost': '127.0.0.1',
        'metricsPromExporterPort': metrics_port,
        # 仅缩短隔离 Broker 的重试间隔，监听器继续用项目的五次重试。
        'messageDelayLevel': ' '.join(['1s'] * 18),
    })
    app_options = {
        'management.endpoints.web.exposure.include': 'health,ordermonitor,catalogcache,prometheus',
        'app.order-messaging.relay-interval-ms': 1000,
        'app.order-messaging.build-dead-letter-enabled': str(consume_dead_letters).lower(),
    }
    env.start_app('ASYNC', overrides=app_options)

    build_group = env.database + '_build'
    baseline = scrape(metrics_port, '/metrics')
    assert 'rocketmq_send_to_dlq_messages_total{' not in baseline
    if prometheus_bin:
        start_prometheus(env, prometheus_bin, metrics_port, prometheus_port, build_group)
    bad_trace = str(uuid.uuid4())
    key = str(uuid.uuid4())
    send = admin(env, 'sendMessage', '-t', env.database, '-c', 'ORDER_BUILD',
                 '-k', key, '-p', json.dumps({'traceId': bad_trace}))
    (env.folder / 'send.log').write_text(send)
    assert 'SEND_OK' in send, send[-1800:]

    def dead_letter_metric():
        return next((line for line in scrape(metrics_port, '/metrics').splitlines()
                     if line.startswith('rocketmq_send_to_dlq_messages_total{')
                     and f'consumer_group="{build_group}"' in line), None)

    def read_dead_letter_count():
        sample = dead_letter_metric()
        return 0 if sample is None else float(sample.rsplit('}', 1)[1].split()[0])

    line = wait_for(dead_letter_metric, 'Broker 建单组死信计数', 110)
    count = float(line.rsplit('}', 1)[1].split()[0])
    assert count == 1, line
    (env.folder / 'broker-dlq.prom').write_text(line + '\n')
    dlq_topic = '%DLQ%' + build_group
    def stored_in_dead_letter():
        details = admin(env, 'queryMsgByKey', '-t', dlq_topic, '-k', key)
        (env.folder / 'dead-letter.log').write_text(details)
        return re.search(r'^[0-9a-fA-F]{16,}[ \t]+\d+[ \t]+\d+[ \t]*$', details, re.MULTILINE)

    wait_for(stored_in_dead_letter, '按原消息 Key 查询到死信正文', 35)
    assert env.sql(f"SELECT COUNT(*) FROM order_submit_trace WHERE trace_id='{bad_trace}'") == '0'
    if consume_dead_letters:
        def reported_as_unmatched():
            log = (env.folder / 'application.log').read_text(errors='replace')
            return any('异步建单死信无法匹配原建单记录' in row and bad_trace in row
                       for row in log.splitlines())
        wait_for(reported_as_unmatched, '独立消费者读取无法匹配的真实死信')
    if prometheus_bin:
        def firing():
            alerts = json.loads(scrape(prometheus_port, '/api/v1/alerts'))['data']['alerts']
            return any(alert['state'] == 'firing'
                       and alert['labels']['alertname'] == 'AsyncOrderBuildDeadLetter'
                       and alert['labels']['consumer_group'] == build_group for alert in alerts)
        wait_for(firing, 'Prometheus 建单死信告警进入 firing', 30)

    # 同一个建单消费组仍然能正常处理有效购买。
    sku = 980010
    env.sql(
        "INSERT INTO product_sku(id,product_id,sku_code,spec,price,original_price,stock,enabled) "
        f"SELECT {sku},product_id,'BUILD-DLQ-SMOKE',spec,price,original_price,10,TRUE "
        "FROM product_sku WHERE id=20101"
    )
    valid_trace = submit(env, sku)
    wait_for(lambda: env.sql(
        f"SELECT CONCAT(status,',',reserve_status) FROM order_submit_trace WHERE trace_id='{valid_trace}'"
    ) == 'SUCCESS,CONFIRMED', '有效订单建单和确认')
    assert env.sql(f"SELECT CONCAT(stock,',',locked_stock) FROM product_sku WHERE id={sku}") == '8,2'

    # 监控运维场景使用静态行。下一次恢复时间置于未来，方便稳定核查每类计数。
    test_rows = (
        ('ASYNC', 'PROCESSING', 'RESERVED', 70),
        ('ASYNC', 'SUCCESS', 'CONFIRM_PENDING', 80),
        ('ASYNC', 'FAILED', 'RELEASE_PENDING', 90),
        ('ASYNC', 'EXPIRED', 'RELEASE_PENDING', 100),
        ('SYNC', 'PROCESSING', 'RESERVED', 500),
        ('SYNC', 'SUCCESS', 'CONFIRM_PENDING', 500),
        ('SYNC', 'FAILED', 'RELEASE_PENDING', 500),
    )
    for mode, status, phase, age in test_rows:
        env.sql(
            "INSERT INTO order_submit_trace(trace_id,user_id,mode,request_snapshot,request_hash,"
            "status,reserve_status,accepted_at,finished_at,processing_expires_at,expires_at,next_check_at) "
            f"VALUES('{uuid.uuid4()}',10001,'{mode}','{{}}',REPEAT('0',64),'{status}','{phase}',"
            f"NOW(6)-INTERVAL {age} SECOND,NOW(6)-INTERVAL {age} SECOND,"
            "NOW(6)+INTERVAL 1 HOUR,NOW(6)+INTERVAL 1 HOUR,NOW(6)+INTERVAL 1 HOUR)"
        )

    def build_stages():
        data = env.api('/actuator/ordermonitor', management=True)['asyncBuild']
        stages = data['stages']
        if data['fresh'] and stages['release_pending']['pending'] == 2:
            return stages
        return None

    stages = wait_for(build_stages, '三类异步积压采样')
    for stage, pending, age in (
        ('processing', 1, 70), ('confirm_pending', 1, 80), ('release_pending', 2, 100)
    ):
        assert stages[stage]['pending'] == pending, stages
        assert age <= stages[stage]['oldestWaitSeconds'] < 300, stages
    app_metrics = scrape(env.management_port, '/actuator/prometheus')
    assert 'mall_order_build_sample_timestamp_seconds ' in app_metrics
    assert 'mall_order_build_oldest_wait_seconds{stage="processing"}' in app_metrics
    result = {'deadLetterCount': count, 'actualDeadLetterTopic': dlq_topic,
              'prometheusAlertFired': bool(prometheus_bin), 'validOrder': 'SUCCESS,CONFIRMED',
              'databaseAvailable': 8, 'stages': stages}
    if consume_dead_letters:
        result['validPurchaseDeadLetterRecovery'] = verify_dead_letter_recovery(
            env, app_options, sku, read_dead_letter_count
        )
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--rocketmq-home', type=Path,
                        default=ROOT / '.runtime/load-infra/rocketmq-all-5.3.2-bin-release')
    parser.add_argument('--prometheus-bin', type=Path, help='可选，验证真实 Prometheus 告警')
    parser.add_argument('--consume-dead-letters', action='store_true', help='同时验证应用主动消费死信')
    args = parser.parse_args()
    stamp = datetime.datetime.now().strftime('%Y%m%d-%H%M%S')
    folder = ROOT / '.runtime/order-build-dlq' / (stamp + '-' + uuid.uuid4().hex[:6])
    env = BenchmarkEnvironment(ROOT, folder, args.rocketmq_home)
    metrics_port = env.free_port()
    while metrics_port in env.ports.values() or abs(metrics_port - env.broker_port) <= 2:
        metrics_port = env.free_port()
    prometheus_port = env.free_port()
    while prometheus_port in [*env.ports.values(), metrics_port] or abs(prometheus_port - env.broker_port) <= 2:
        prometheus_port = env.free_port()
    report = {'passed': False}
    try:
        report.update(verify(env, metrics_port, args.prometheus_bin, prometheus_port,
                             args.consume_dead_letters))
        report['passed'] = True
    finally:
        env.close()
        report['isolatedDatabaseDropped'] = not env.created
        (folder / 'report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2))
        print(f'报告 {folder / "report.json"}', flush=True)


if __name__ == '__main__':
    main()
