#!/usr/bin/env python3
"""使用本机 JMeter 复测商品详情及同一目标速率下的同步、异步下单。"""
import argparse
import csv
import datetime
import json
import math
import subprocess
import time
import uuid
import xml.etree.ElementTree as ET
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from benchmark_environment import BenchmarkEnvironment

ROOT = Path(__file__).resolve().parents[1]


def property_of(parent, name, value, kind="stringProp"):
    ET.SubElement(parent, kind, {"name": name}).text = str(value)


def add(parent, name, label, guiclass=None):
    return ET.SubElement(parent, name, {
        "guiclass": guiclass or name + "Gui", "testclass": name, "testname": label, "enabled": "true"
    })


def plan(path, port, feed, mode, threads, iterations, rate, seconds):
    order = mode in {"SYNC", "ASYNC"}
    doc = ET.Element("jmeterTestPlan", {"version": "1.2", "properties": "5.0", "jmeter": "5.6.3"})
    root = ET.SubElement(doc, "hashTree")
    add(root, "TestPlan", "商城订单或商品详情")
    children = ET.SubElement(root, "hashTree")
    group = add(children, "ThreadGroup", "商城请求", "ThreadGroupGui")
    property_of(group, "ThreadGroup.on_sample_error", "continue")
    controller = ET.SubElement(group, "elementProp", {
        "name": "ThreadGroup.main_controller", "elementType": "LoopController", "guiclass": "LoopControlPanel",
        "testclass": "LoopController", "testname": "循环控制器", "enabled": "true"
    })
    property_of(controller, "LoopController.loops", iterations if order else -1)
    property_of(group, "ThreadGroup.num_threads", threads)
    property_of(group, "ThreadGroup.ramp_time", 2)
    if not order:
        property_of(group, "ThreadGroup.scheduler", "true", "boolProp")
        property_of(group, "ThreadGroup.duration", seconds)
    group_tree = ET.SubElement(children, "hashTree")

    dataset = add(group_tree, "CSVDataSet", "每笔使用原始购买编号" if order else "多个商品编号", "TestBeanGUI")
    for name, value in {"filename": feed, "fileEncoding": "UTF-8", "delimiter": ",",
                        "variableNames": "traceId" if order else "productId", "shareMode": "shareMode.all"}.items():
        property_of(dataset, name, value)
    property_of(dataset, "recycle", "false" if order else "true", "boolProp")
    property_of(dataset, "stopThread", "true" if order else "false", "boolProp")
    ET.SubElement(group_tree, "hashTree")

    if order:
        timer = add(group_tree, "ConstantThroughputTimer", "相同目标下单速率", "TestBeanGUI")
        target = ET.SubElement(timer, "doubleProp")
        ET.SubElement(target, "name").text = "throughput"
        ET.SubElement(target, "value").text = str(rate * 60)
        ET.SubElement(target, "savedValue").text = "0.0"
        property_of(timer, "calcMode", 4, "intProp")
        ET.SubElement(group_tree, "hashTree")

    sampler = add(group_tree, "HTTPSamplerProxy", mode, "HttpTestSampleGui")
    for name, value in {"domain": "127.0.0.1", "port": port, "protocol": "http", "implementation": "HttpClient4",
                        "path": "/api/store/orders/submit" if order else "/api/store/products/${productId}",
                        "method": "POST" if order else "GET", "connect_timeout": 4000, "response_timeout": 12000}.items():
        property_of(sampler, f"HTTPSampler.{name}", value)
    property_of(sampler, "HTTPSampler.use_keepalive", "true", "boolProp")
    if order:
        property_of(sampler, "HTTPSampler.postBodyRaw", "true", "boolProp")
        arguments = ET.SubElement(sampler, "elementProp", {"name": "HTTPsampler.Arguments", "elementType": "Arguments"})
        values = ET.SubElement(arguments, "collectionProp", {"name": "Arguments.arguments"})
        body = ET.SubElement(values, "elementProp", {"name": "", "elementType": "HTTPArgument"})
        property_of(body, "HTTPArgument.always_encode", "false", "boolProp")
        property_of(body, "Argument.value", '${purchaseJson}')
        property_of(body, "Argument.metadata", "=")
    sampler_tree = ET.SubElement(group_tree, "hashTree")
    if order:
        prepare = add(sampler_tree, "JSR223PreProcessor", "准备同一份购买参数", "TestBeanGUI")
        property_of(prepare, "scriptLanguage", "groovy")
        property_of(prepare, "cacheKey", "true")
        property_of(prepare, "script", """def body = props.getProperty('purchaseJson')
vars.put('purchaseJson', body.replace('__TRACE__', vars.get('traceId')))""")
        ET.SubElement(sampler_tree, "hashTree")
        headers = add(sampler_tree, "HeaderManager", "JSON 请求", "HeaderPanel")
        items = ET.SubElement(headers, "collectionProp", {"name": "HeaderManager.headers"})
        header = ET.SubElement(items, "elementProp", {"name": "", "elementType": "Header"})
        property_of(header, "Header.name", "Content-Type")
        property_of(header, "Header.value", "application/json")
        ET.SubElement(sampler_tree, "hashTree")
    check = add(sampler_tree, "JSR223PostProcessor", "核对业务响应", "TestBeanGUI")
    property_of(check, "scriptLanguage", "groovy")
    property_of(check, "cacheKey", "true")
    property_of(check, "script", """
vars.put('submitStatus', '')
if (prev.getResponseCode() != '200') return
def data = new groovy.json.JsonSlurper().parseText(prev.getResponseDataAsString())
""" + (
        """vars.put('submitStatus', data.status ?: '')
def ok = data.traceId == vars.get('traceId') && data.mode == props.getProperty('orderMode') &&
    ((data.status == 'SUCCESS' && data.orderId) || data.status == 'PROCESSING')
prev.setSuccessful(ok as boolean)
""" if order else
        "prev.setSuccessful(data.id?.toString() == vars.get('productId'))\n"))
    ET.SubElement(sampler_tree, "hashTree")
    ET.indent(doc)
    ET.ElementTree(doc).write(path, encoding="utf-8", xml_declaration=True)


def percentiles(values):
    values = sorted(values)
    if not values:
        return {"count": 0}
    return {
        "count": len(values), "mean": round(sum(values) / len(values), 3),
        **{name: values[math.ceil(len(values) * fraction) - 1]
           for name, fraction in [("p50", .5), ("p95", .95), ("p99", .99), ("max", 1)]}
    }


def read_results(path):
    with path.open() as stream:
        rows = list(csv.DictReader(stream))
    successful = [row for row in rows if row["success"] == "true"]
    timestamps = [(int(row["timeStamp"]), int(row["elapsed"])) for row in rows]
    duration = (max(start + elapsed for start, elapsed in timestamps) - min(start for start, _ in timestamps)) / 1000
    return {
        "total": len(rows), "successful": len(successful), "seconds": duration,
        "successQps": len(successful) / duration,
        "httpSuccessMs": percentiles([int(row["elapsed"]) for row in successful]),
        "httpErrorMs": percentiles([int(row["elapsed"]) for row in rows if row["success"] != "true"]),
        "codes": {code: sum(row["responseCode"] == code for row in rows)
                  for code in sorted({row["responseCode"] for row in rows})},
        "clientStartEpochMs": min(start for start, _ in timestamps),
        "clientEndEpochMs": max(start + elapsed for start, elapsed in timestamps)
    }, successful


def report_stage(env, args, mode):
    order = mode in {"SYNC", "ASYNC"}
    feed = env.folder / "input.csv"
    purchase = {}
    env.start()
    local_mode = "OFF" if order else args.detail_cache_mode
    env.start_app(mode if order else "SYNC", local_mode, one_second=True)

    if order:
        env.sql("INSERT INTO product_sku(id,product_id,sku_code,spec,price,original_price,stock,enabled) "
                "SELECT 900001,product_id,'JMETER',spec,price,original_price,5000000,TRUE FROM product_sku WHERE id=20501")
        quote = env.api('/api/store/checkout/quote', body={
            "userId": 10001, "source": "DIRECT", "skuId": 900001, "quantity": 1, "addressId": 20001
        })
        purchase = {
            "userId": 10001, "traceId": "__TRACE__", "purchase": {
                "source": "DIRECT", "skuId": 900001, "quantity": 1, "addressId": 20001,
                "expectedPayAmount": quote["payAmount"], "expectedItems": [{"skuId": 900001, "quantity": 1}]
            }
        }
        def token(_):
            return env.api('/api/store/order-tokens', body={})['traceId']
        # 独立暖身订单，JMeter 样本与此批凭证分离。
        for _ in range(8):
            body = dict(purchase, traceId=token(None))
            response = env.api('/api/store/orders/submit', body=body)
            assert response["status"] in {"SUCCESS", "PROCESSING"}
        env.drain(timeout=30)
        with ThreadPoolExecutor(max_workers=12) as pool:
            feed.write_text(''.join(trace + '\n' for trace in pool.map(token, range(args.threads * args.iterations))))
    else:
        product_ids = env.sql("SELECT id FROM product WHERE enabled=TRUE ORDER BY id LIMIT 64").splitlines()
        for product_id in product_ids:
            for _ in range(2):
                assert env.api("/api/store/products/" + product_id)["id"] == int(product_id)
        feed.write_text('\n'.join(product_ids) + '\n')

    plan_file = env.folder / "benchmark.jmx"
    result_file = env.folder / "samples.jtl"
    threads = args.threads if order or args.detail_threads is None else args.detail_threads
    plan(plan_file, env.app_port, feed, mode, threads, args.iterations, args.rate, args.seconds)
    before = env.api('/actuator/catalogcache', True)['counters']
    args_list = [str(args.jmeter.resolve()), '-n', '-t', str(plan_file), '-l', str(result_file),
                 '-j', str(env.folder / 'jmeter.log'), '-Jsample_variables=traceId,productId,submitStatus',
                 '-JpurchaseJson=' + json.dumps(purchase, separators=(',', ':')),
                 '-JorderMode=' + mode, '-Jsampleresult.timestamp.start=true', '-Jjmeterengine.nongui.port=0']
    with (env.folder / 'jmeter-console.log').open('w') as log:
        subprocess.run(args_list, cwd=env.folder, stdout=log, stderr=subprocess.STDOUT, check=True, timeout=120)
    result, successes = read_results(result_file)
    result.update(mode=mode, threads=threads)
    if order:
        result['targetRate'] = args.rate
        result['iterationsPerThread'] = args.iterations
        result['pendingAtEnd'] = env.outstanding()
        drain = env.drain(timeout=args.drain_timeout, output=env.folder / 'drain.json', allow_timeout=True)
        result['drained'] = drain['drained']
        result['drainSeconds'] = drain['seconds']
        result['pendingAfterDrain'] = env.outstanding()
        pairs = env.sql("SELECT trace_id,status,UNIX_TIMESTAMP(finished_at)*1000 FROM order_submit_trace "
                        "WHERE finished_at IS NOT NULL")
        (env.folder / 'db-terminal.tsv').write_text(pairs + '\n')
        done = {trace: float(when) for trace, state, when in (row.split('\t') for row in pairs.splitlines())
                if state == 'SUCCESS'}
        result['submitToDbFinishedMs'] = percentiles([round(done[row['traceId']] - int(row['timeStamp']), 3)
            for row in successes if row['traceId'] in done])
        result['createdForSuccessfulResponses'] = sum(row['traceId'] in done for row in successes)
        if drain['drained']:
            result['consistency'] = env.consistency()
        else:
            result['unfinishedOutbox'] = env.sql(
                'SELECT event_type,status,COUNT(*) FROM order_message_outbox GROUP BY event_type,status')
    else:
        result['productCount'] = len(product_ids)
        result['localMode'] = local_mode
        counters = env.api('/actuator/catalogcache', True)['counters']
        result['cacheCounters'] = {key: value - before.get(key, 0) for key, value in counters.items()}

    time.sleep(max(.0, math.floor(time.time()) + 1.02 - time.time()))
    throughput = env.api('/actuator/throughput', True)
    (env.folder / 'throughput.json').write_text(json.dumps(throughput, indent=2))
    left = math.ceil(result['clientStartEpochMs'] / 1000)
    right = math.floor(result['clientEndEpochMs'] / 1000)
    complete = [row for row in throughput['seconds'] if left <= row['epochSecond'] < right]
    result['completeWindowSeconds'] = max(0, right - left)
    result['peak1s'] = {key: max((row[key] for row in complete), default=0)
        for key in ('querySuccess', 'submitHttpSuccess', 'asyncAccepted', 'ordersCreated')}
    (env.folder / 'result.json').write_text(json.dumps(result, ensure_ascii=False, indent=2))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jmeter", type=Path, required=True)
    parser.add_argument("--threads", type=int, default=32)
    parser.add_argument("--detail-threads", type=int)
    parser.add_argument("--detail-cache-mode", choices=["ALL", "OFF"], default="ALL",
                        help="商品详情场景是否启用本地商品资料缓存")
    parser.add_argument("--iterations", type=int, default=125)
    parser.add_argument("--rate", type=int, default=200)
    parser.add_argument("--seconds", type=int, default=22)
    parser.add_argument("--drain-timeout", type=int, default=90)
    parser.add_argument("--scenarios", choices=["SYNC", "ASYNC", "DETAIL"], nargs="+",
                        default=["SYNC", "ASYNC", "DETAIL"])
    args = parser.parse_args()
    args.jmeter = args.jmeter.resolve()
    folder = ROOT / ".runtime/jmeter-tests" / (datetime.datetime.now().strftime('%Y%m%d-%H%M%S') + '-' + uuid.uuid4().hex[:6])
    folder.mkdir(parents=True)
    print(f"报告目录 {folder}", flush=True)
    results = []
    for mode in args.scenarios:
        env = BenchmarkEnvironment(ROOT, folder / mode.lower(), ROOT / '.runtime/load-infra/rocketmq-all-5.3.2-bin-release')
        try:
            result = report_stage(env, args, mode)
            results.append(result)
            print(json.dumps({key: value for key, value in result.items() if key != "consistency"}, ensure_ascii=False), flush=True)
        finally:
            env.close()
            (folder / 'report.json').write_text(json.dumps(results, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
