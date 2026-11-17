#!/usr/bin/env python3
"""独立 JVM / 独立 Redisson 客户端，共享临时 Redis，验证多 key 冷构建合并；不是容量峰值压测。"""
import datetime,importlib.util,json,uuid
from pathlib import Path
from benchmark_environment import BenchmarkEnvironment

ROOT=Path(__file__).resolve().parents[1]

def main():
    loader=importlib.util.spec_from_file_location('multikey_verifier',ROOT/'scripts/run-multikey-cache-test.py')
    module=importlib.util.module_from_spec(loader);loader.loader.exec_module(module)
    folder=ROOT/'.runtime/redisson-cache-tests'/(datetime.datetime.now().strftime('%Y%m%d-%H%M%S')+'-'+uuid.uuid4().hex[:6])
    env=BenchmarkEnvironment(ROOT,folder,ROOT/'.runtime/load-infra/rocketmq-all-5.3.2-bin-release')
    report={'runDirectory':str(folder),'purpose':'cross-JVM Redisson cache reconstruction correctness'}
    settings={'app.order-messaging.enabled':'false','app.catalog-cache.detail-ttl':'60s',
              'app.catalog-protection.bloom-rebuild-interval-ms':3600000}
    try:
        env.start();env.start_app('SYNC','OFF',True,settings)
        env.stop(env.app)
        module.seed_catalog(env,1_000_000,64)
        # 只处理本轮私有命名空间；不能在真实交易 Redis 清空库存。
        prefix='peak:'+env.database+':catalog:{catalog}:bloom:redisson:v2:'
        keys=module.redis(env,'--scan','--pattern','*'+prefix+'*').splitlines()
        if keys:module.redis(env,'DEL',*keys)
        env.start_app('SYNC','OFF',True,settings)
        expected=int(env.sql('SELECT COUNT(*) FROM product'))
        module.wait_bloom(env,expected)
        peers=[module.peer(env)]
        old_ports={env.app_port,env.management_port,env.cache_port,env.stock_port}
        env.app_port=env.free_port()
        while env.app_port in old_ports:env.app_port=env.free_port()
        env.management_port=env.free_port()
        while env.management_port in old_ports|{env.app_port}:env.management_port=env.free_port()
        env.start_app('SYNC','OFF',True,settings,replace=False)
        module.wait_bloom(env,expected);peers.append(module.peer(env))
        report['result']=module.finite_cold(env,peers,folder/'dual-jvm-cold',1_000_000,64)
        report['passed']=True
    finally:
        env.close();report['isolatedProcessesStopped']=True;report['isolatedDatabaseDropped']=not env.created
        (folder/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
        print(json.dumps(report,ensure_ascii=False),flush=True)
if __name__=='__main__':main()
