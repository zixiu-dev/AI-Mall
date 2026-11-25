#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""递增并发/速率寻找实际吞吐平台。每个场景独立数据库，固定资源，保留错误、积压和客户端开销。"""
import argparse,csv,datetime,http.client,json,math,os,re,signal,subprocess,threading,time
from pathlib import Path
from benchmark_environment import BenchmarkEnvironment

ROOT=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--rocketmq-home',type=Path,default=ROOT/'.runtime/load-infra/rocketmq-all-5.3.2-bin-release')
parser.add_argument('--wrk',type=Path,default=ROOT/'.runtime/load-infra/wrk/wrk')
parser.add_argument('--scenarios',default='l1,redis,sync-hot,async-hot,sync-spread,async-spread')
parser.add_argument('--probe-seconds',type=int,default=20)
parser.add_argument('--warmup-seconds',type=int,default=10)
parser.add_argument('--drain-timeout',type=int,default=600)
parser.add_argument('--query-product-id',type=int,default=205)
parser.add_argument('--order-template-sku',type=int,default=20501)
parser.add_argument('--confirm-seconds',type=int,default=60)
parser.add_argument('--order-workers',type=int,default=4)
parser.add_argument('--backlog-limit',type=int,default=1500)
parser.add_argument('--order-rates',default='50,100,200,400,800,1200,2000,3200',help='订单递增探测速率，逗号分隔')
parser.add_argument('--refine-steps',type=int,default=2)
parser.add_argument('--confirmation-rate',type=int,help='明确指定一分钟确认档位；仍按相同规则判定，不保证通过')
parser.add_argument('--one-second',action='store_true',help='启用服务端固定自然秒桶，统计真正的一秒 QPS/TPS')
parser.add_argument('--peak-only',action='store_true',help='仅升压寻找一秒峰值，不重复之前的长时确认；必须配合 --one-second')
parser.add_argument('--query-concurrencies',default='8,16,32,64,128,256,512')
args=parser.parse_args()
if args.peak_only and not args.one_second:parser.error('--peak-only 必须配合 --one-second')
run=ROOT/'.runtime/peak-tests'/(datetime.datetime.now().strftime('%Y%m%d-%H%M%S')+'-'+os.urandom(3).hex());run.mkdir(parents=True)
(run/'runner.pid').write_text(str(os.getpid()))
report={'runDirectory':str(run),'startedAt':datetime.datetime.now().isoformat(),'topology':'one JVM 512MB, MySQL, two Redis, one local RocketMQ broker, generators on the same host','resources':{'hikariMax':16,'submitConcurrency':32,'consumerThreads':'2..4','logicalCpuCount':os.cpu_count()},'method':{'query':'native wrk 4.2.0, two threads, HTTP/1.1 keep-alive, closed-loop concurrency sweep','orders':'four open-loop Node workers with unique backend-issued traces, independent submission and committed-order counters','backlogStopLimit':args.backlog_limit,'orderProbeRates':args.order_rates,'explicitConfirmationRate':args.confirmation_rate,'oneSecondBuckets':args.one_second,'peakOnly':args.peak_only,'probeSeconds':args.probe_seconds,'confirmSeconds':args.confirm_seconds,'stableRule':'HTTP errors <=1%, client drops <=1%, target delivery >=98%, async outstanding <=max(5,1% of accepted) and committed/accepted >=97%'},'scenarios':[]}
active_workers=[]
def save(): (run/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
def print_result(label,value):print(label+' '+json.dumps(value,ensure_ascii=False),flush=True)
def delta(after,before):return {k:after.get(k,0)-before.get(k,0) for k in set(after)|set(before)}
def percentile(hist,p):
    remaining=math.ceil(sum(hist)*p)
    for i,count in enumerate(hist):
        remaining-=count
        if remaining<=0:return round(math.expm1(i*math.log(1.02)),3)
    return 0

def one_second_result(env,directory,start_ms,end_ms,raw=None):
    """直接读取事件计数桶，不用两次轮询差分估算一秒；丢弃首尾不完整自然秒。"""
    # 等最后一个事件所属秒结束，避免把当前只过了几百毫秒的桶当成完整一秒。
    if raw is None:
        time.sleep(max(0, math.floor(time.time())+1.02-time.time()))
        raw=env.api('/actuator/throughput',True)
    assert raw['enabled'] and raw['bucketWidthMillis']==1000
    (directory/'one-second-raw.json').write_text(json.dumps(raw,indent=2))
    left=math.ceil(start_ms/1000);right=math.floor(end_ms/1000)
    fields=['queryResponses','querySuccess','submitResponses','submitHttpSuccess','asyncAccepted','ordersCreated','createdSync','createdAsync']
    observed={r['epochSecond']:r for r in raw['seconds']}
    series=[observed.get(s,dict(epochSecond=s,**{k:0 for k in fields})) for s in range(left,right)]
    peaks={}
    for field in fields:
        row=max(series,key=lambda r:r[field]) if series else None
        peaks[field]=dict(value=row[field],epochSecond=row['epochSecond'],bucket=row) if row else None
    result={'definition':'server event counts in complete UTC epoch-aligned [s,s+1) seconds; HTTP handled responses, TPS afterCommit','measurementStartEpochMs':start_ms,'measurementEndEpochMs':end_ms,'completeSeconds':len(series),'peaks':peaks,'seconds':series}
    (directory/'one-second.json').write_text(json.dumps(result,indent=2));return result

def collect_scene_seconds(entry,source="oneSecond"):
    """跨不同发压档位取完整一秒的最高值；过载仍可产生有效一秒，但保留拒绝和停止原因。"""
    result={}
    for row in entry['stages']:
        for field,peak in row.get(source,{}).get('peaks',{}).items():
            if peak and (field not in result or peak['value']>result[field]['value']):
                result[field]=dict(peak,stage=row['name'],guard=row['resources']['guard'])
    return result
class Sampler:
    """每秒独立采样资源和建单积压；超过保护值只停止发新请求，不中断已受理订单。"""
    def __init__(self,env,directory,orders=False):
        self.env=env;self.directory=directory;self.orders=orders;self.rows=[];self.stop_event=threading.Event();self.guard=None;self.extra=[]
    def start(self):self.thread=threading.Thread(target=self.loop,daemon=True);self.thread.start()
    def loop(self):
        while not self.stop_event.is_set():
            row={'epochMs':time.time()*1000}
            try:
                row['orders']=self.env.api('/actuator/ordermonitor',True,timeout=2)
                for name in ['hikaricp.connections.active','hikaricp.connections.pending','jvm.gc.pause','jvm.memory.used']:
                    try:row[name]=self.env.api('/actuator/metrics/'+name,True,timeout=2)['measurements']
                    except Exception:pass
                processes={p.pid:n for n,p in self.env.processes if p.poll() is None};processes.update({p.pid:'generator' for p in self.extra if p.poll() is None})
                try:processes[int((self.env.root/'.runtime/mysql/mysql.pid').read_text())]='mysql'
                except Exception:pass
                try:
                    rows=subprocess.check_output(['ps','-p',','.join(map(str,processes)),'-o','pid=,pcpu=,rss='],text=True,timeout=2).splitlines();row['processes']=[{'name':processes[int(x[0])],'pid':int(x[0]),'cpuPercent':float(x[1]),'rssKb':int(x[2])} for x in (r.split() for r in rows) if len(x)==3 and int(x[0]) in processes]
                except Exception:pass
                if self.orders:
                    count,age=self.env.sql("SELECT COUNT(*),COALESCE(TIMESTAMPDIFF(SECOND,MIN(accepted_at),CURRENT_TIMESTAMP(6)),0) FROM order_submit_trace WHERE status='PROCESSING' OR reserve_status IN ('RESERVING','RESERVED','CONFIRM_PENDING','RELEASE_PENDING','RETURN_PENDING')").split('\t');row['queuePending']=int(count);row['queueOldestSeconds']=int(age)
                    if int(count)>args.backlog_limit or int(age)>30:
                        self.guard='BACKLOG_LIMIT'
                        for p in self.extra:
                            if p.poll() is None:os.kill(p.pid,signal.SIGUSR1)
            except Exception as ex:row['samplingError']=type(ex).__name__
            self.rows.append(row);self.stop_event.wait(1)
    def close(self):
        self.stop_event.set();self.thread.join(timeout=12);(self.directory/'resources.json').write_text(json.dumps(self.rows,indent=2))
    def summary(self):
        cpus={}
        for row in self.rows:
            grouped={}
            for process in row.get('processes',[]):grouped[process['name']]=grouped.get(process['name'],0)+process['cpuPercent']
            for name,value in grouped.items():cpus.setdefault(name,[]).append(value)
        return {'cpuPercentAverage':{k:round(sum(v)/len(v),2) for k,v in cpus.items()},'cpuPercentMax':{k:round(max(v),2) for k,v in cpus.items()},'maxPending':max([r.get('queuePending',0) for r in self.rows] or [0]),'guard':self.guard,'samples':len(self.rows)}

def query_step(env,label,concurrency,seconds):
    # 查询用长连接闭环发压：固定并发持续请求，按 wrk 自身的实际计时统计响应吞吐。
    directory=env.folder/label;directory.mkdir();before=env.api('/actuator/catalogcache',True)['counters'];sampler=Sampler(env,directory)
    log=(directory/'wrk.log').open('w');started=time.monotonic();start_epoch_ms=time.time()*1000
    process=subprocess.Popen([str(args.wrk.resolve()),'-t2','-c'+str(concurrency),'-d'+str(seconds)+'s','--latency','--timeout','5s','-s',str(ROOT/'scripts/wrk-summary.lua'),'http://127.0.0.1:'+str(env.app_port)+'/api/store/products/'+str(args.query_product_id)],stdout=log,stderr=subprocess.STDOUT);active_workers.append(process);sampler.extra=[process];sampler.start()
    try:process.wait(timeout=seconds+20);finished=time.monotonic();end_epoch_ms=time.time()*1000
    finally:BenchmarkEnvironment.stop(process);sampler.close();log.close()
    text=(directory/'wrk.log').read_text(errors='replace');match=re.search(r'PEAK_JSON (.*)',text)
    if not match:raise RuntimeError('wrk 未输出结果 '+str(directory))
    raw=json.loads(match.group(1));elapsed=raw['durationMicros']/1e6
    if elapsed<seconds*.95:raise RuntimeError('wrk 未运行完整时长')
    completed=raw['requests'];transport=raw['connectErrors']+raw['readErrors']+raw['writeErrors']+raw['timeouts'];errors=raw['statusErrors']+transport
    after=env.api('/actuator/catalogcache',True)['counters'];counters=delta(after,before);hit=counters.get('local_hit',0)+counters.get('redis_hit',0);loads=counters.get('database_load',0)
    result={'name':label,'tool':'wrk 4.2.0 HTTP/1.1','concurrency':concurrency,'seconds':elapsed,'wallSeconds':finished-started,'completed':completed,'errors':errors,'errorRate':errors/max(1,completed+transport),'successfulQps':max(0,completed-raw['statusErrors'])/elapsed,'p50Ms':raw['p50us']/1000,'p95Ms':raw['p95us']/1000,'p99UpperBoundMs':raw['p99us']/1000,'cacheCounters':counters,'hitRatio':hit/max(1,hit+loads),'resources':sampler.summary(),'raw':raw}
    result['stable']=result['errorRate']<=.01 and process.returncode==0 and result['hitRatio']>.99
    if args.one_second:result['oneSecond']=one_second_result(env,directory,start_epoch_ms,end_epoch_ms)
    (directory/'result.json').write_text(json.dumps(result,indent=2));print_result('QUERY',result);return result

def latency_stats(values):
    """逐笔实测毫秒的最近秩分位，并保留平均值与最大值。"""
    if not values:
        return {'count': 0}
    values = sorted(values)
    result = {'count': len(values), 'mean': round(sum(values) / len(values), 3)}
    for name, fraction in [('p50', .5), ('p95', .95), ('p99', .99), ('max', 1)]:
        result[name] = round(values[math.ceil(len(values) * fraction) - 1], 3)
    return result

def order_latencies(env, directory, worker_rows):
    """用 trace 将 HTTP 样本和数据库终态配对，分别统计响应、排队到建单和提交到建单。"""
    samples = {item['traceId']: item for row in worker_rows for item in row['traces']}
    successful = [item for item in samples.values()
                  if item['httpCode'] == 200 and item.get('status') in {'SUCCESS', 'PROCESSING'}]
    failed = [item for item in samples.values()
              if item['httpCode'] != 200 or item.get('status') not in {'SUCCESS', 'PROCESSING'}]
    # 数据库时间戳位于建单事务内。TPS 单独使用 afterCommit 秒桶。
    sql = ("SELECT trace_id, status, COALESCE(UNIX_TIMESTAMP(accepted_at)*1000,0), "
           "COALESCE(UNIX_TIMESTAMP(finished_at)*1000,0) "
           "FROM order_submit_trace WHERE finished_at IS NOT NULL")
    paired, accept_to_build, submit_to_build, async_wait_ms = [], [], [], []
    for line in env.sql(sql).splitlines():
        trace_id, status, accepted_ms, finished_ms = line.split('\t')
        item = samples.get(trace_id)
        if item is None:
            continue
        accepted_ms, finished_ms = float(accepted_ms), float(finished_ms)
        paired.append({'traceId': trace_id, 'status': status,
                       'acceptedEpochMs': accepted_ms, 'finishedEpochMs': finished_ms})
        if status == 'SUCCESS':
            submit_to_build.append(finished_ms - item['submitEpochMs'])
            if accepted_ms:
                accept_to_build.append(finished_ms - accepted_ms)
            if item.get('status') == 'PROCESSING':
                async_wait_ms.append(finished_ms - item['submitEpochMs'] - item['httpMs'])
    (directory/'db-terminal-pairs.json').write_text(json.dumps(paired, ensure_ascii=False))
    return {
        'httpSuccessMs': latency_stats([item['httpMs'] for item in successful]),
        'httpErrorMs': latency_stats([item['httpMs'] for item in failed]),
        'submitToDbFinishedMs': latency_stats(submit_to_build),
        'dbAcceptedToFinishedMs': latency_stats(accept_to_build),
        'asyncHttpReplyToDbFinishedMs': latency_stats(async_wait_ms),
        'httpAcceptedWithFinalOrder': sum(item['status'] == 'SUCCESS'
             and samples[item['traceId']]['httpCode'] == 200
             and samples[item['traceId']].get('status') in {'SUCCESS', 'PROCESSING'} for item in paired),
        'httpFailedButOrderCreated': sum(item['status'] == 'SUCCESS'
             and (samples[item['traceId']]['httpCode'] != 200
                  or samples[item['traceId']].get('status') not in {'SUCCESS', 'PROCESSING'}) for item in paired),
    }

def order_step(env,label,rate,seconds,purchases):
    # 下单用开环到达率，先完成后端发号，再用统一屏障同时启动四个进程。
    directory=env.folder/label;directory.mkdir();barrier=directory/'start.json';workers=[];configs=[]
    for index in range(args.order_workers):
        config={'baseUrl':'http://127.0.0.1:'+str(env.app_port),'rate':rate/args.order_workers,'seconds':seconds,'inflight':64,'purchases':purchases[index:]+purchases[:index],'ready':str(directory/('ready-'+str(index))),'start':str(barrier),'output':str(directory/('worker-'+str(index)+'.json'))};file=directory/('input-'+str(index)+'.json');file.write_text(json.dumps(config));log=(directory/('worker-'+str(index)+'.log')).open('w');p=subprocess.Popen(['node',str(ROOT/'scripts/peak-order-worker.mjs'),str(file)],stdout=log,stderr=subprocess.STDOUT);workers.append(p);active_workers.append(p);configs.append(config)
    until=time.monotonic()+240
    while not all(Path(c['ready']).exists() for c in configs):
        if any(p.poll() is not None for p in workers) or time.monotonic()>until:
            for p in workers:BenchmarkEnvironment.stop(p)
            raise RuntimeError('预发 trace 未完成 '+str(directory))
        time.sleep(.1)
    before=env.api('/actuator/ordermonitor',True);cache_before=env.api('/actuator/catalogcache',True)['counters'];start=time.time()*1000+300;barrier.write_text(json.dumps({'epochMs':start}));sampler=Sampler(env,directory,True);sampler.extra=workers;sampler.start()
    try:
        for p in workers:p.wait(timeout=seconds+25)
        after=env.api('/actuator/ordermonitor',True);outstanding=env.outstanding()
    finally:
        for p in workers:BenchmarkEnvironment.stop(p)
        sampler.close()
    rows=[json.loads(Path(c['output']).read_text()) for c in configs];elapsed=(after['sampledAtEpochMs']-start)/1000
    codes={};modes={};http_hist=[0]*800;arrival_hist=[0]*800
    for r in rows:
        for key,n in r['codes'].items():codes[key]=codes.get(key,0)+n
        for key,n in r['modes'].items():modes[key]=modes.get(key,0)+n
        for i,n in enumerate(r['httpHistogram']):http_hist[i]+=n
        for i,n in enumerate(r['arrivalHistogram']):arrival_hist[i]+=n
    # 受理成功数和事务提交计数分开读取，PROCESSING 不能冒充已创建订单。
    completed=sum(r['completed'] for r in rows);dropped=sum(r['dropped'] for r in rows);success=sum(r['accepted'] for r in rows);commits=after['ordersCreatedTotal']-before['ordersCreatedTotal'];
    result={'name':label,'targetRate':rate,'requestedSeconds':seconds,'measuredSeconds':elapsed,'completed':completed,'successfulResponses':success,'codes':codes,'modes':modes,'clientDropped':dropped,'haltedWorkers':sum(bool(r.get('halted',False)) for r in rows),'successfulSubmitQps':success/elapsed,'totalResponseQps':completed/elapsed,'committedOrdersInWindow':commits,'businessTps':commits/elapsed,'pendingAtEnd':outstanding,'httpP99Ms':percentile(http_hist,.99),'arrivalP99Ms':percentile(arrival_hist,.99),'workers':[{'cpuPercent':r['cpuPercent'],'eventLoopP99Ms':r['eventLoopP99Ms']} for r in rows],'resources':sampler.summary()}
    result['stable']=elapsed>=seconds*.95 and not any(r.get('halted',False) for r in rows) and (completed-success)/max(1,completed)<=.01 and dropped/max(1,completed+dropped)<=.01 and success/max(1,rate*elapsed)>=.98 and outstanding<=max(5,success*.01) and commits/max(1,success)>=.97 and sampler.guard is None
    if args.one_second:
        stop_ms = min(start + seconds * 1000, after['sampledAtEpochMs'])
        result['oneSecond'] = one_second_result(env, directory, start, stop_ms)
    # 即使过载后排空超时，也先保留窗口内的失败、受理和 TPS 数据。
    (directory/'result-before-drain.json').write_text(json.dumps(result,indent=2))
    drained=env.drain(timeout=args.drain_timeout,output=directory/'drain.json',allow_timeout=True);result['drained']=drained['drained'];result['drainSeconds']=drained['seconds'];(directory/'drain.json').write_text(json.dumps(drained,indent=2))
    if args.one_second:
        drain_dir=directory/'drain-seconds';drain_dir.mkdir()
        time.sleep(max(0, math.floor(time.time()) + 1.02 - time.time()))
        raw_seconds=env.api('/actuator/throughput',True)
        result['drainOneSecond']=one_second_result(env,drain_dir,after['sampledAtEpochMs'],raw_seconds['snapshotAtEpochMs'],raw_seconds)
        whole_dir=directory/'whole-seconds';whole_dir.mkdir()
        result['wholeOneSecond']=one_second_result(env,whole_dir,start,raw_seconds['snapshotAtEpochMs'],raw_seconds)
    # 有积压时记录真实消费 TPS；负载停止后“清队列”的吞吐不能冒充入口受理能力。
    result['latency']=order_latencies(env,directory,rows)
    intervals=[]
    for left,right in zip(drained['samples'],drained['samples'][1:]):
        if left['pending']>20:intervals.append((right['created']-left['created'])/max(.001,(right['epochMs']-left['epochMs'])/1000))
    result['drainConsumerTpsMax1s']=max(intervals or [0]);result['finalCreatedTotal']=env.api('/actuator/ordermonitor',True)['ordersCreatedTotal'];result['cacheCounterDelta']=delta(env.api('/actuator/catalogcache',True)['counters'],cache_before)
    (directory/'result.json').write_text(json.dumps(result,indent=2));print_result('ORDER',result);return result

try:
    for scenario in args.scenarios.split(','):
        assert scenario in ['l1','redis','sync-hot','async-hot','sync-spread','async-spread']
        env=BenchmarkEnvironment(ROOT,run/scenario,args.rocketmq_home);entry={'name':scenario,'stages':[]};report['scenarios'].append(entry);save();print('START '+scenario,flush=True)
        try:
            env.start()
            is_query=scenario in ['l1','redis']
            mode='ASYNC' if scenario.startswith('async') else 'SYNC'
            env.start_app(mode,'ALL' if scenario=='l1' else 'OFF',args.one_second)
            entry['ports']=env.ports
            if is_query:
                connection=http.client.HTTPConnection('127.0.0.1',env.app_port,timeout=5)
                connection.request('GET','/api/store/products/'+str(args.query_product_id));response=connection.getresponse();response.read();first_port=connection.sock.getsockname()[1] if connection.sock else None
                connection.request('GET','/api/store/products/'+str(args.query_product_id));response=connection.getresponse();response.read();second_port=connection.sock.getsockname()[1] if connection.sock else None
                entry['http11KeepAliveVerified']=response.version==11 and first_port is not None and first_port==second_port;connection.close();assert entry['http11KeepAliveVerified']
                # HTTP 预热真实缓存，不通过模拟热点探测器伪造本地命中。
                for _ in range(5):assert env.api('/api/store/products/'+str(args.query_product_id))['id']==args.query_product_id
                warm=query_step(env,'warmup',16,args.warmup_seconds);entry['warmup']=warm
                flat=0;best=None
                for concurrency in [int(value) for value in args.query_concurrencies.split(',')]:
                    row=query_step(env,'probe-c'+str(concurrency),concurrency,args.probe_seconds);entry['stages'].append(row);save()
                    if best is None or (row['stable'] and row['successfulQps']>best['successfulQps']):
                        improved=best is None or row['successfulQps']>best['successfulQps']*1.05;flat=0 if improved else flat+1;best=row
                    else:flat+=1
                    if flat>=2 or row['errorRate']>.05:break
                if args.peak_only:
                    assert best and best['stable']
                    desired='local_hit' if scenario=='l1' else 'redis_hit'
                    assert best['cacheCounters'].get(desired,0)>best['completed']*.98
                    if scenario=='redis':assert best['cacheCounters'].get('local_hit',0)==0
                    entry['passed']=True;entry['selectedStage']=best['name']
                else:
                    assert best and best['stable'];confirmation=query_step(env,'confirm-c'+str(best['concurrency']),best['concurrency'],args.confirm_seconds);entry['confirmation']=confirmation;entry['peakObservedQps']=max(r['successfulQps'] for r in entry['stages']);entry['confirmedQps']=confirmation['successfulQps'];entry['passed']=confirmation['stable']
                    desired='local_hit' if scenario=='l1' else 'redis_hit';other='redis_hit' if scenario=='l1' else 'local_hit';assert confirmation['cacheCounters'].get(desired,0)>confirmation['completed']*.98
                    if scenario=='redis':assert confirmation['cacheCounters'].get(other,0)==0
            else:
                sku_count=64 if scenario.endswith('spread') else 1
                for i in range(sku_count):env.sql("INSERT INTO product_sku(id,product_id,sku_code,spec,price,original_price,stock,enabled) SELECT "+str(900001+i)+",product_id,'PEAK-"+str(i)+"',spec,price,original_price,5000000,TRUE FROM product_sku WHERE id="+str(args.order_template_sku)+" AND enabled=TRUE")
                q=env.api('/api/store/checkout/quote',body={'userId':10001,'source':'DIRECT','skuId':900001,'quantity':1,'addressId':20001})
                purchases=[{'source':'DIRECT','skuId':900001+i,'quantity':1,'addressId':20001,'expectedPayAmount':q['payAmount'],'expectedItems':[{'skuId':900001+i,'quantity':1}]} for i in range(sku_count)]
                entry['skuCount']=sku_count;entry['warmup']=order_step(env,'warmup',20,args.warmup_seconds,purchases);best=None;failed=None;flat=0;peak=0
                for rate in [int(value) for value in args.order_rates.split(',')]:
                    row=order_step(env,'probe-r'+str(rate),rate,args.probe_seconds,purchases);entry['stages'].append(row);save()
                    if row['stable']:best=row
                    else:failed=rate
                    flat=flat+1 if row['businessTps']<peak*1.05 else 0;peak=max(peak,row['businessTps'])
                    if not row['drained']:
                        entry['unfinishedStage'] = row['name']
                        break
                    if args.peak_only:
                        if row['resources']['guard']:break
                    elif not row['stable'] or flat>=2:break
                if args.peak_only:
                    entry['validation']='complete one-second event buckets; check stock conservation on drained scenarios'
                    entry['passed']=True
                    if args.confirmation_rate is not None and 'unfinishedStage' not in entry:
                        confirmation=order_step(env,'confirm-r'+str(args.confirmation_rate),
                                                args.confirmation_rate,args.confirm_seconds,purchases)
                        entry['confirmation']=confirmation
                        entry['stages'].append(confirmation)
                        if not confirmation['drained']:
                            entry['unfinishedStage'] = confirmation['name']
                elif 'unfinishedStage' not in entry:
                    # 找到边界后缩小区间，避免把 200 与 400 之间的真实能力粗略说成只有 200。
                    if best and failed:
                        low=best['targetRate'];high=failed
                        for _ in range(args.refine_steps):
                            middle=int((low+high)/2)
                            if middle<=low:break
                            row=order_step(env,'refine-r'+str(middle),middle,args.probe_seconds,purchases);entry['stages'].append(row);save()
                            if row['stable']:best=row;low=middle
                            else:high=middle
                    if best is None and args.confirmation_rate is None:raise RuntimeError('探测档位均未达到稳定判据，需要更低档位测量')
                    confirmation_rate=args.confirmation_rate if args.confirmation_rate is not None else best['targetRate']
                    confirmation=order_step(env,'confirm-r'+str(confirmation_rate),confirmation_rate,args.confirm_seconds,purchases)
                    if not confirmation['stable']:
                        # 60 秒确认才是稳定结果；短探测成功而长测不稳时向下降档。
                        candidates=sorted([r for r in entry['stages'] if r['stable'] and r['targetRate']<confirmation_rate],key=lambda r:r['targetRate'],reverse=True)
                        for candidate in candidates:
                            entry.setdefault('failedConfirmations',[]).append(confirmation);confirmation=order_step(env,'confirm-r'+str(candidate['targetRate']),candidate['targetRate'],args.confirm_seconds,purchases)
                            if confirmation['stable']:break
                    entry['confirmation']=confirmation;entry['peakObservedTps']=max([r['businessTps'] for r in entry['stages'] if r['measuredSeconds']>=r['requestedSeconds']*.95 and not r.get('haltedWorkers',0)] or [0]);entry['peakAcceptedQps']=max([r['successfulSubmitQps'] for r in entry['stages'] if r['measuredSeconds']>=r['requestedSeconds']*.95 and not r.get('haltedWorkers',0)] or [0]);entry['confirmedTps']=confirmation['businessTps'];entry['passed']=confirmation['stable']
                if 'unfinishedStage' in entry:
                    entry['passed']=False
                    entry['pendingBeforeCleanup'] = env.outstanding()
                    entry['unfinishedOutbox'] = env.sql(
                        'SELECT event_type,status,COUNT(*) FROM order_message_outbox GROUP BY event_type,status')
                else:
                    entry['consistency']=env.consistency()
                # 保留每笔数据库终态时间，可独立复核异步完成而不是只看 HTTP 200。
                (env.folder/'order-completions.tsv').write_text(env.sql("SELECT trace_id,mode,status,UNIX_TIMESTAMP(created_at)*1000,UNIX_TIMESTAMP(finished_at)*1000 FROM order_submit_trace ORDER BY created_at"))
            if args.one_second:
                entry['oneSecondPeaks']=collect_scene_seconds(entry)
                if not is_query:entry['wholeOneSecondPeaks']=collect_scene_seconds(entry,'wholeOneSecond')
            save();print_result('SCENARIO_DONE',{k:v for k,v in entry.items() if k not in ['stages','warmup','confirmation','consistency']})
        finally:env.close()
    report['passed']=all(s.get('passed',False) for s in report['scenarios']);report['finishedAt']=datetime.datetime.now().isoformat();save()
finally:
    for p in active_workers:BenchmarkEnvironment.stop(p)
    save();print('REPORT '+str(run/'report.json'),flush=True)
