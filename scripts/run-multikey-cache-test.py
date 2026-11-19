#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""多个真实商品 key：冷缓存合并、热缓存读取、TTL 周期重建和双 JVM 分布式合并。只操作本轮临时资源。"""
import argparse
import datetime
import json
import math
import os
import re
import subprocess
import time
import urllib.request
import uuid
from pathlib import Path

from benchmark_environment import BenchmarkEnvironment

ROOT = Path(__file__).resolve().parents[1]
FIELDS = ['queryResponses', 'querySuccess', 'cacheLoads', 'cacheFills',
          'cacheRedisWaits', 'cacheDoubleCheckHits', 'ordersCreated']


def api(base, path):
    with urllib.request.urlopen(base + path, timeout=15) as response:
        return json.load(response)


def redis(env, *arguments):
    return subprocess.check_output(['redis-cli', '-p', str(env.cache_port), '--raw',
                                    *map(str, arguments)], text=True).strip()


def counters(peers):
    values = {}
    for peer in peers:
        for key, value in api(peer['management'], '/actuator/catalogcache')['counters'].items():
            values[key] = values.get(key, 0) + value
    return values


def delta(after, before):
    return {key: after.get(key, 0) - before.get(key, 0) for key in set(after) | set(before)}


def peer(env):
    return {'http': 'http://127.0.0.1:' + str(env.app_port),
            'management': 'http://127.0.0.1:' + str(env.management_port)}


def capture_seconds(peers, directory, start_ms, end_ms, finite=False):
    """直接读取服务端事件秒桶。有限冷冲击可包含最后一秒的空闲余量，绝不按比例放大。"""
    stop = math.ceil((end_ms + 1) / 1000) if finite else math.floor(end_ms / 1000)
    # 等事件所在最后一秒结束，snapshot 只提供已经关闭的自然秒。
    close = math.ceil((end_ms + 1) / 1000) * 1000 + 20
    time.sleep(max(0, (close - time.time() * 1000) / 1000))
    merged = {}
    for index, item in enumerate(peers):
        raw = api(item['management'], '/actuator/throughput')
        assert raw['enabled'] and raw['bucketWidthMillis'] == 1000
        (directory / ('raw-seconds-' + str(index) + '.json')).write_text(json.dumps(raw, indent=2))
        for row in raw['seconds']:
            target = merged.setdefault(row['epochSecond'], dict(epochSecond=row['epochSecond'],
                                        **{field: 0 for field in FIELDS}))
            for field in FIELDS:
                target[field] += row.get(field, 0)
    series = [merged.get(second, dict(epochSecond=second, **{field: 0 for field in FIELDS}))
              for second in range(math.ceil(start_ms / 1000), stop)]
    peaks = {field: max(series, key=lambda row: row[field]) if series else None for field in FIELDS}
    active = [row for row in series if row['cacheLoads'] > 0]
    result = {'bucketMillis': 1000, 'seconds': series, 'peaks': peaks,
              'rebuildActiveSeconds': len(active),
              'highestQpsWhileRebuilding': max(active, key=lambda row: row['querySuccess']) if active else None,
              'tailMayIncludeIdle': finite, 'startEpochMs': start_ms, 'endEpochMs': end_ms}
    (directory / 'seconds.json').write_text(json.dumps(result, indent=2))
    return result


def seed_catalog(env, first_id, count):
    """复制真实商品的全部 SKU/属性；每份商品、SKU 均有不同 ID，不共用同一个详情 key。"""
    ids = ','.join('(' + str(i) + ')' for i in range(first_id, first_id + count))
    sql = f"""
    START TRANSACTION;
    CREATE TEMPORARY TABLE benchmark_ids(id BIGINT PRIMARY KEY);
    INSERT INTO benchmark_ids VALUES {ids};
    INSERT INTO product(id,name,category,brand,summary,image_key,rating,sales_count,enabled)
      SELECT b.id,CONCAT(p.name,' load-',b.id),p.category,p.brand,p.summary,p.image_key,p.rating,p.sales_count,TRUE
      FROM benchmark_ids b JOIN product p ON p.id=108;
    INSERT INTO product_sku(id,product_id,sku_code,spec,price,original_price,stock,enabled)
      SELECT b.id*10000+s.id,b.id,CONCAT('BENCH-',b.id,'-',s.id),s.spec,s.price,s.original_price,s.stock,s.enabled
      FROM benchmark_ids b JOIN product_sku s ON s.product_id=108;
    INSERT INTO product_attribute(product_id,attr_key,attr_value)
      SELECT b.id,a.attr_key,a.attr_value FROM benchmark_ids b JOIN product_attribute a ON a.product_id=108;
    INSERT INTO sku_attribute(sku_id,attr_key,attr_value)
      SELECT b.id*10000+s.id,a.attr_key,a.attr_value FROM benchmark_ids b
      JOIN product_sku s ON s.product_id=108 JOIN sku_attribute a ON a.sku_id=s.id;
    COMMIT;
    """
    subprocess.run(env.mysql + ['--database=' + env.database], input=sql, text=True,
                   check=True, capture_output=True)
    assert int(env.sql(f'SELECT COUNT(*) FROM product WHERE id>={first_id} AND id<{first_id+count}')) == count


def wait_bloom(env, expected):
    metadata = 'peak:' + env.database + ':catalog:{catalog}:bloom:redisson:v2:metadata'
    until = time.monotonic() + 30
    while time.monotonic() < until:
        if redis(env, 'HGET', metadata, 'ready') == '1' and redis(env, 'HGET', metadata, 'count') == str(expected):
            return
        time.sleep(.2)
    raise RuntimeError('布隆过滤器未包含全部新商品，不能开始压测')


def reset_details(env, peers, directory):
    """清本次隔离测试的展示键；逐个广播资料失效，不触碰库存池和 Bloom。"""
    namespace = 'peak:' + env.database + ':catalog'
    prefix = namespace + ':{catalog}:display:'
    keys = redis(env, '--scan', '--pattern', prefix + '*').splitlines()
    for start in range(0, len(keys), 256):
        batch = keys[start:start+256]
        redis(env, 'EVAL', "for _,k in ipairs(KEYS) do redis.call('DEL',k);redis.call('PUBLISH',ARGV[1],string.sub(k,tonumber(ARGV[2]))) end;return 1",
              len(batch), *batch, namespace + ':display:invalidate', len(prefix)+1)
    until = time.monotonic() + 5
    while time.monotonic() < until:
        if all(api(p['management'], '/actuator/catalogcache')['localEntries'] == 0 for p in peers):
            break
        time.sleep(.05)
    assert all(api(p['management'], '/actuator/catalogcache')['localEntries'] == 0 for p in peers)
    pattern = prefix + 'product:*'
    assert redis(env, '--scan', '--pattern', pattern) == ''
    (directory / 'cache-reset.json').write_text(json.dumps({'removedDisplayKeys': len(keys),
         'namespace': namespace, 'coldDetailKeys': 0, 'localEntries': 0}, indent=2))
    return pattern


def finite_cold(env, peers, directory, first, count, copies=8):
    """四个进程、共八个 key 组；每个组同时访问同一 key 八次，明确制造锁内二次检查。"""
    directory.mkdir(); pattern = reset_details(env, peers, directory)
    before = counters(peers); processes = []; configs = []
    for index in range(4):
        config = {'ids': list(range(first + index, first + count, 4)),
                  'origins': [p['http'] for p in peers], 'groups': 2, 'copies': copies,
                  'ready': str(directory / ('ready-' + str(index))),
                  'barrier': str(directory / 'start.json'), 'output': str(directory / ('worker-' + str(index) + '.json'))}
        path = directory / ('input-' + str(index) + '.json'); path.write_text(json.dumps(config)); configs.append(config)
        log = (directory / ('worker-' + str(index) + '.log')).open('w')
        processes.append(subprocess.Popen(['node', str(ROOT / 'scripts/cache-cold-worker.mjs'), str(path)], stdout=log, stderr=subprocess.STDOUT))
    try:
        until = time.monotonic() + 15
        while not all(Path(c['ready']).exists() for c in configs):
            if any(p.poll() is not None for p in processes) or time.monotonic() > until:
                raise RuntimeError('冷构建客户端启动失败')
            time.sleep(.05)
        start = math.ceil(time.time() + 1) * 1000
        (directory / 'start.json').write_text(json.dumps({'epochMs': start}))
        for process in processes: process.wait(timeout=120)
    finally:
        for process in processes: BenchmarkEnvironment.stop(process)
    rows = [json.loads(Path(c['output']).read_text()) for c in configs]
    finished = max(row['finishedEpochMs'] for row in rows)
    changes = delta(counters(peers), before); codes = {}
    for row in rows:
        for code, number in row['codes'].items(): codes[code] = codes.get(code, 0) + number
    covered = {key for row in rows for key in row['keys']}
    assert len(covered) == count
    assert sum(row['valid'] for row in rows) == count * copies, codes
    assert changes.get('database_load', 0) == count, changes
    assert changes.get('rebuild_filled', 0) == count, changes
    assert changes.get('rebuild_wait', 0) + changes.get('rebuild_avoided_after_redis_lock', 0) > 0, changes
    assert changes.get('bloom_bypassed', 0) == changes.get('bloom_rejected', 0) == 0, changes
    if len(peers) > 1:
        assert changes.get('rebuild_wait', 0) + changes.get('rebuild_avoided_after_redis_lock', 0) > 0, changes
    cache_keys = redis(env, '--scan', '--pattern', pattern).splitlines()
    assert len(cache_keys) >= count
    latencies = sorted(value for row in rows for value in row['latencies'])
    result = {'name': directory.name, 'type': 'finite-cold-cohorts', 'instances': len(peers),
              'keys': count, 'copiesPerKey': copies, 'requests': count * copies, 'codes': codes,
              'allProductAndSkuIdsValidated': True, 'elapsedSeconds': (finished - start) / 1000,
              'p99Ms': latencies[math.ceil(len(latencies) * .99) - 1], 'cacheDelta': changes,
              'cpuPercentWorkers': [r['cpuPercent'] for r in rows],
              'oneSecond': capture_seconds(peers, directory, start, finished, finite=True), 'passed': True}
    (directory / 'result.json').write_text(json.dumps(result, indent=2)); return result


def continuous(env, peers, directory, first, count, concurrency, seconds):
    """高吞吐多 key 读取。工作集均匀轮转，原始秒桶同时给出该秒的回源次数。"""
    directory.mkdir(); before = counters(peers); start = time.time() * 1000
    with (directory / 'wrk.log').open('w') as log:
        process = subprocess.Popen([str(ROOT / '.runtime/load-infra/wrk/wrk'), '-t2', '-c' + str(concurrency),
            '-d' + str(seconds) + 's', '--latency', '--timeout', '5s', '-s', str(ROOT / 'scripts/wrk-multikey.lua'),
            peers[0]['http'], '--', str(first), str(count)], stdout=log, stderr=subprocess.STDOUT)
        try: process.wait(timeout=seconds + 20)
        finally: BenchmarkEnvironment.stop(process)
    end = time.time() * 1000
    text = (directory / 'wrk.log').read_text(); match = re.search(r'PEAK_JSON (.*)', text)
    if not match: raise RuntimeError('wrk 未产生多 key 统计：' + text[-1500:])
    raw = json.loads(match.group(1)); assert raw['distinctKeysRequested'] == count and raw['minIssuedPerKey'] > 0
    elapsed = raw['durationMicros'] / 1e6
    errors = sum(raw[k] for k in ['statusErrors', 'connectErrors', 'readErrors', 'writeErrors', 'timeouts'])
    changes = delta(counters(peers), before)
    result = {'name': directory.name, 'type': 'continuous-uniform-key-permutation', 'keys': count,
              'concurrency': concurrency, 'seconds': elapsed, 'completed': raw['requests'], 'errors': errors,
              'successfulQpsAverage': (raw['requests'] - raw['statusErrors']) / elapsed,
              'p99Ms': raw['p99us'] / 1000, 'cacheDelta': changes, 'wrk': raw,
              'oneSecond': capture_seconds(peers, directory, start, end),
              'withinOnePercentError': errors / max(1, raw['requests']) <= .01}
    assert changes.get('bloom_bypassed', 0) == changes.get('bloom_rejected', 0) == 0, changes
    (directory / 'result.json').write_text(json.dumps(result, indent=2)); return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--keys', type=int, default=1024)
    parser.add_argument('--seconds', type=int, default=20)
    parser.add_argument('--concurrencies', default='16,32,64,128')
    parser.add_argument('--modes', default='ALL,OFF')
    args = parser.parse_args()
    assert 64 <= args.keys <= 1600
    run = ROOT / '.runtime/multikey-cache-tests' / (datetime.datetime.now().strftime('%Y%m%d-%H%M%S') + '-' + uuid.uuid4().hex[:6])
    run.mkdir(parents=True); (run / 'runner.pid').write_text(str(os.getpid()))
    report = {'runDirectory': str(run), 'startedAt': datetime.datetime.now().isoformat(),
              'keys': args.keys, 'firstProductId': 1_000_000, 'scenarios': [],
              'method': 'finite cold cohorts + warmed multi-key + continuous TTL rebuild; fixed server 1000ms buckets'}
    def save(): (run / 'report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2))
    base = {'app.order-messaging.enabled': 'false'}
    long_ttl = dict(base, **{'app.catalog-cache.detail-ttl': '60s', 'app.catalog-cache.local-ttl': '60s'})
    short_ttl = dict(base, **{'app.catalog-cache.detail-ttl': '2s', 'app.catalog-cache.local-ttl': '1s'})
    try:
        for mode in args.modes.split(','):
            assert mode in ['ALL', 'OFF']
            env = BenchmarkEnvironment(ROOT, run / mode, ROOT / '.runtime/load-infra/rocketmq-all-5.3.2-bin-release')
            scene = {'localMode': mode, 'stages': []}; report['scenarios'].append(scene); save(); print('START ' + mode, flush=True)
            try:
                env.start(); env.start_app('SYNC', mode, True, long_ttl)
                # 先完成 Flyway，再离线导入；重启前丢弃本轮旧 Bloom，防止未登记的新商品被误判。
                env.stop(env.app); seed_catalog(env, 1_000_000, args.keys)
                prefix = 'peak:' + env.database + ':catalog:{catalog}:bloom:redisson:v2:'
                redis(env, 'DEL', *[prefix + item for item in ['active', 'staging', 'metadata', 'lease']])
                env.start_app('SYNC', mode, True, long_ttl); peers = [peer(env)]
                wait_bloom(env, int(env.sql('SELECT COUNT(*) FROM product')))
                # 只预热独立的控制商品 108/JIT，绝不访问正式工作集的 1024 个 key。
                continuous(env, peers, env.folder / 'jit-control-only', 108, 1, 16, 5)
                stage = finite_cold(env, peers, env.folder / 'cold-1024', 1_000_000, args.keys)
                scene['stages'].append(stage); save(); print('COLD ' + json.dumps({'mode': mode, 'loads': stage['cacheDelta']['database_load'], 'requests': stage['requests']}), flush=True)
                stage = continuous(env, peers, env.folder / 'warm-multikey', 1_000_000, args.keys, 64, args.seconds)
                assert stage['cacheDelta'].get('database_load', 0) == 0
                scene['stages'].append(stage); save(); print('WARM ' + mode, flush=True)
                env.start_app('SYNC', mode, True, short_ttl); peers = [peer(env)]
                wait_bloom(env, int(env.sql('SELECT COUNT(*) FROM product')))
                reset_folder = env.folder / 'ttl-reset'; reset_folder.mkdir(); reset_details(env, peers, reset_folder)
                for concurrency in map(int, args.concurrencies.split(',')):
                    stage = continuous(env, peers, env.folder / ('ttl-c' + str(concurrency)), 1_000_000, args.keys, concurrency, args.seconds)
                    assert stage['cacheDelta'].get('database_load', 0) > args.keys * 2, stage['cacheDelta']
                    assert stage['oneSecond']['highestQpsWhileRebuilding'] is not None
                    scene['stages'].append(stage); save()
                    row = stage['oneSecond']['highestQpsWhileRebuilding']
                    print('TTL ' + json.dumps({'mode': mode, 'c': concurrency, 'peakQpsWithLoads': row['querySuccess'], 'loadsInPeakSecond': row['cacheLoads'], 'errors': stage['errors']}), flush=True)
                if mode == 'OFF':
                    # 双 JVM 共享本轮 DB/Redis，只用于验证跨进程锁，不混入单实例峰值。
                    main_peer = peers[0]; env.app_port = env.free_port(); env.management_port = env.free_port()
                    env.start_app('SYNC', mode, True, long_ttl, replace=False); peers = [main_peer, peer(env)]
                    time.sleep(1.1)
                    stage = finite_cold(env, peers, env.folder / 'two-jvm-cold-64', 1_000_000, 64)
                    scene['stages'].append(stage); save(); print('TWO_JVM ' + json.dumps(stage['cacheDelta']), flush=True)
                scene['passed'] = True; save()
            finally:
                (env.folder / 'owned-processes.json').write_text(json.dumps([{'name': n, 'pid': p.pid} for n, p in env.processes], indent=2))
                env.close()
        report['finishedAt'] = datetime.datetime.now().isoformat(); report['passed'] = True
    finally:
        save(); print('REPORT ' + str(run / 'report.json'), flush=True)


if __name__ == '__main__': main()
