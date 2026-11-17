#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""隔离的真实 MySQL + 两个 Redis + 本机 RocketMQ 压测。使用临时库，不连接共享开发 MQ。"""
import argparse,datetime,json,os,platform,re,signal,socket,subprocess,threading,time,urllib.parse,urllib.request,uuid
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--rocketmq-home',required=True,type=Path,help='已解压的官方 RocketMQ 二进制目录（包含 lib）')
parser.add_argument('--high-rate',type=int,default=90)
parser.add_argument('--sync-budget',type=int,default=60)
args=parser.parse_args()
assert (args.rocketmq_home/'lib').is_dir(),'RocketMQ lib directory not found'
run=ROOT/'.runtime/load-tests'/(datetime.datetime.now().strftime('%Y%m%d-%H%M%S')+'-'+uuid.uuid4().hex[:6]);run.mkdir(parents=True)
(run/'runner.pid').write_text(str(os.getpid()))
processes=[];database='bitmall_load_'+uuid.uuid4().hex[:12]
metadata=json.loads((ROOT/'.runtime/mysql/connection.json').read_text())
mysql=['/usr/local/mysql/bin/mysql','--defaults-extra-file='+str(ROOT/'.runtime/mysql/root.cnf'),'-N','-B']
def sql(statement,db=False):return subprocess.check_output(mysql+(['--database='+database] if db else [])+['-e',statement],text=True).strip()
def port():
 with socket.socket() as s:s.bind(('127.0.0.1',0));return s.getsockname()[1]
def launch(command,filename,env=None):
 log=(run/filename).open('a');p=subprocess.Popen(command,cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT,start_new_session=True);processes.append(p);return p
def stop(p):
 if p.poll() is None:
  p.terminate()
  try:p.wait(timeout=15)
  except subprocess.TimeoutExpired:p.kill();p.wait(timeout=5)
def wait_port(value,process,seconds=40):
 until=time.monotonic()+seconds
 while time.monotonic()<until:
  if process.poll() is not None:raise RuntimeError('Infrastructure exited; inspect logs in '+str(run))
  try:
   with socket.create_connection(('127.0.0.1',value),timeout=.2):return
  except OSError:time.sleep(.2)
 raise RuntimeError('Port not ready: '+str(value))
def api(path,management=False,body=None):
 req=urllib.request.Request(('http://127.0.0.1:'+str(management_port if management else app_port))+path,data=None if body is None else json.dumps(body).encode(),headers={'Content-Type':'application/json'})
 with urllib.request.urlopen(req,timeout=10) as response:return json.load(response)
allocated=set()
while len(allocated)<5:allocated.add(port())
app_port,management_port,cache_port,stock_port,namesrv_port=allocated
# Broker 使用相邻的主端口/VIP/HA 端口，三个都确认空闲。
while True:
 broker_port=port();sockets=[]
 if {broker_port-2,broker_port,broker_port+1}&{app_port,management_port,cache_port,stock_port,namesrv_port}:continue
 try:
  for number in [broker_port-2,broker_port,broker_port+1]:
   s=socket.socket();s.bind(('127.0.0.1',number));sockets.append(s)
  break
 except OSError:pass
 finally:
  for s in sockets:s.close()
(run/'ports.json').write_text(json.dumps({'app':app_port,'management':management_port,'cache':cache_port,'stock':stock_port,'nameserver':namesrv_port,'broker':broker_port}))
report={'runDirectory':str(run),'database':database,'topology':'1 application + MySQL + 2 Redis + 1 RocketMQ broker, all on same host','machine':{'platform':platform.platform(),'logicalCpuCount':os.cpu_count()},'phases':[]}
monitor_stop=threading.Event();monitor_rows=[];app=None;created=False
try:
 sql('CREATE DATABASE `'+database+'` CHARACTER SET utf8mb4 COLLATE utf8mb4_bin');created=True
 user=metadata['username'];assert re.fullmatch(r'[a-zA-Z0-9_]+',user)
 for host in sql("SELECT Host FROM mysql.user WHERE User='"+user+"'").splitlines():
  assert re.fullmatch(r'[a-zA-Z0-9._%:-]+',host);sql("GRANT ALL PRIVILEGES ON `"+database+"`.* TO '"+user+"'@'"+host+"'")
 for kind,value in [('cache',cache_port),('stock',stock_port)]:
  directory=run/kind;directory.mkdir();p=launch(['redis-server','--bind','127.0.0.1','--port',str(value),'--save','','--appendonly','no','--maxmemory-policy','noeviction' if kind=='stock' else 'allkeys-lru','--dir',str(directory)],kind+'.log');wait_port(value,p)
 namesrv_config=run/'namesrv.conf';namesrv_config.write_text('listenPort='+str(namesrv_port)+'\nbindAddress=127.0.0.1\n')
 mq_home=run/'mq-home';mq_home.mkdir();classpath=str(args.rocketmq_home.resolve()/'lib'/'*')
 common=['java','-Duser.home='+str(mq_home),'-Drocketmq.home.dir='+str(args.rocketmq_home.resolve()),'--add-opens=java.base/java.nio=ALL-UNNAMED','--add-opens=java.base/sun.nio.ch=ALL-UNNAMED']
 namesrv=launch(common+['-Xms64m','-Xmx128m','-cp',classpath,'org.apache.rocketmq.namesrv.NamesrvStartup','-c',str(namesrv_config)],'namesrv.log');wait_port(namesrv_port,namesrv)
 broker_config=run/'broker.conf';broker_config.write_text('\n'.join(['brokerClusterName=IsolatedLoadTest','brokerName=load-'+database,'brokerId=0','brokerIP1=127.0.0.1','bindAddress=127.0.0.1','listenPort='+str(broker_port),'namesrvAddr=127.0.0.1:'+str(namesrv_port),'storePathRootDir='+str(run/'store'),'storePathCommitLog='+str(run/'store'/'commitlog'),'brokerRole=ASYNC_MASTER','flushDiskType=ASYNC_FLUSH','autoCreateTopicEnable=true','mappedFileSizeCommitLog=16777216','mappedFileSizeConsumeQueue=600000','diskMaxUsedSpaceRatio=95']))
 broker=launch(common+['-Xms128m','-Xmx384m','-XX:MaxDirectMemorySize=128m','-cp',classpath,'org.apache.rocketmq.broker.BrokerStartup','-c',str(broker_config)],'broker.log');wait_port(broker_port,broker)
 # TCP 主端口打开不代表 VIP/HA 已绑定成功；显式建 Topic 验证 Broker 可管理且已注册。
 admin=common+['-Xms32m','-Xmx128m','-cp',classpath,'org.apache.rocketmq.tools.command.MQAdminStartup']
 until=time.monotonic()+40
 while True:
  if broker.poll() is not None:raise RuntimeError('Broker exited before readiness check')
  with (run/'admin.log').open('a') as log:
   checked=subprocess.run(admin+['updateTopic','-n','127.0.0.1:'+str(namesrv_port),'-b','127.0.0.1:'+str(broker_port),'-t',database,'-r','4','-w','4'],stdout=log,stderr=subprocess.STDOUT,timeout=10)
  if checked.returncode==0:break
  if time.monotonic()>until:raise RuntimeError('Broker topic readiness failed')
  time.sleep(.5)
 env={key:value for key,value in os.environ.items() if not key.startswith(("SPRING_","ROCKETMQ_","ORDER_","CATALOG_","HOTKEY_","AGENT_","OPENAI_","MANAGEMENT_","SERVER_","LOGGING_"))}
 # 环境完全覆盖服务地址与身份，禁止继承真实模型/共享 MQ 的连接和凭据。
 env.update(DB_USERNAME=metadata['username'],DB_PASSWORD=metadata['password'],AGENT_AI_ENABLED='false',HOTKEY_ENABLED='false',ROCKETMQ_ACCESS_KEY='',ROCKETMQ_SECRET_KEY='',ORDER_SUBMISSION_MODE='AUTO',ORDER_INVENTORY_RESERVATION_ENABLED='true',ORDER_NAMESPACE='load:'+database+':',CATALOG_CACHE_NAMESPACE='load:'+database+':catalog')
 env.pop('JAVA_TOOL_OPTIONS',None);env.pop('JDK_JAVA_OPTIONS',None);env.pop('REDISCLI_AUTH',None)
 jdbc=re.sub(r'(/)[^/?]+(?=\?|$)',r'\g<1>'+database,metadata['url'],count=1)
 jar=list((ROOT/'target').glob('*.jar'));assert len(jar)==1,'Run mvn package first'
 options=['--spring.profiles.active=loadtest','--spring.datasource.url='+jdbc,'--spring.datasource.hikari.maximum-pool-size=16','--spring.data.redis.host=127.0.0.1','--spring.data.redis.port='+str(cache_port),'--app.order-processing.redis-port='+str(stock_port),'--app.order-processing.locks-enabled=true','--app.order-routing.sync-qps-per-instance='+str(args.sync_budget),'--app.order-messaging.enabled=true','--app.order-messaging.topic='+database,'--app.order-messaging.consumer-group='+database+'_events','--app.order-messaging.build-consumer-group='+database+'_build','--rocketmq.producer.group='+database+'_producer','--rocketmq.name-server=127.0.0.1:'+str(namesrv_port),'--server.address=127.0.0.1','--server.port='+str(app_port),'--management.server.address=127.0.0.1','--management.server.port='+str(management_port),'--management.endpoints.web.exposure.include=health,prometheus,ordermonitor,catalogcache','--app.catalog-cache.detail-ttl=2s','--app.catalog-cache.search-ttl=2s','--logging.level.root=WARN','--logging.level.org.springframework=WARN','--logging.level.com.bitmall.agent=INFO','--debug=false','--trace=false','--app.agent.ai-enabled=false','--app.hotkey.enabled=false']
 def start_app(cache):
  p=launch(['java','-Xms128m','-Xmx512m','-jar',str(jar[0]),*options,'--app.catalog-cache.enabled='+str(cache).lower()],'application.log',env)
  until=time.monotonic()+60
  while time.monotonic()<until:
   if p.poll() is not None:raise RuntimeError('Application exited; inspect '+str(run/'application.log'))
   try:
    state=api('/actuator/ordermonitor',True)
    if state['routing']['fresh']:return p
   except Exception:pass
   time.sleep(.3)
  raise RuntimeError('Application not ready')
 app=start_app(False)
 sql("INSERT INTO product_sku(id,product_id,sku_code,spec,price,original_price,stock,enabled) SELECT 900001,product_id,'ISOLATED-LOAD',spec,price,original_price,20000,TRUE FROM product_sku WHERE id=1003",True)
 purchase={'userId':10001,'source':'DIRECT','skuId':900001,'quantity':1,'addressId':20001,'expectedItems':[{'skuId':900001,'quantity':1}]};purchase['expectedPayAmount']=api('/api/store/checkout/quote',body=purchase)['payAmount']
 def monitor():
  while not monitor_stop.wait(1):
   try:monitor_rows.append({'timestamp':datetime.datetime.now().isoformat(),**api('/actuator/ordermonitor',True)['routing']})
   except Exception:pass
 thread=threading.Thread(target=monitor,daemon=True);thread.start()
 def phase(name,kind,rate,seconds,repeat=None):
  config={'name':name,'kind':kind,'rate':rate,'seconds':seconds,'baseUrl':'http://127.0.0.1:'+str(app_port),'monitorUrl':'http://127.0.0.1:'+str(management_port)+'/actuator/ordermonitor','maxInFlight':64,'purchase':purchase,'repeatBody':repeat,'output':str(run/(name+'.json'))}
  path=run/(name+'-input.json');path.write_text(json.dumps(config));before=api('/actuator/catalogcache',True)['counters'];print('START '+name,flush=True)
  subprocess.run(['node',str(ROOT/'scripts/http-load.mjs'),str(path)],check=True,stdout=(run/(name+'.log')).open('w'))
  result=json.loads(Path(config['output']).read_text());after=api('/actuator/catalogcache',True)['counters'];result['cacheCounterDelta']={k:after.get(k,0)-before.get(k,0) for k in set(before)|set(after)}
  report['phases'].append({k:v for k,v in result.items() if k!='traces'});print('DONE '+json.dumps(report['phases'][-1]),flush=True);return result
 # 两种查询路径都先预热，基线与缓存在相同到达速率下比较；另有较高压力观察。
 phase('database-warmup','detail',30,3);phase('database-baseline','detail',100,8)
 stop(app);app=start_app(True);phase('cache-warmup','detail',30,3);phase('cache-same-rate','detail',100,8);phase('cache-high-rate','detail',1000,8);phase('invalid-id-penetration','missing',800,8);phase('search-cache','search',200,8)
 phase('orders-low','order',5,8);high=phase('orders-high','order',args.high_rate,20);phase('orders-recovery','order',3,42)
 # 等待真实消费者和库存整理完成，再做一致性核对；不把 HTTP 受理成功当建单成功。
 until=time.monotonic()+120
 while time.monotonic()<until:
  outstanding=int(sql("SELECT COUNT(*) FROM order_submit_trace WHERE status='PROCESSING' OR reserve_status IN ('RESERVING','RESERVED','CONFIRM_PENDING','RELEASE_PENDING','RETURN_PENDING')",True))
  if outstanding==0:break
  print('DRAIN remaining='+str(outstanding),flush=True);time.sleep(2)
 else:raise RuntimeError('Orders did not drain before timeout')
 succeeded=[t for t in high['traces'] if t['status']=='SUCCESS']
 if not succeeded:
  row=sql("SELECT trace_id FROM order_submit_trace WHERE status='SUCCESS' LIMIT 1",True);assert row;succeeded=[{'traceId':row}]
 phase('duplicate-submissions','repeat',40,5,{'userId':10001,'traceId':succeeded[0]['traceId'],'purchase':purchase})
 # 聚合核对数据库与 Redis，另外输出最终状态，不依赖客户端收到的响应是否完整。
 states=sql("SELECT status,COUNT(*) FROM order_submit_trace GROUP BY status",True)
 stock,locked=map(int,sql('SELECT stock,locked_stock FROM product_sku WHERE id=900001',True).split('\t'))
 order_count=int(sql('SELECT COUNT(*) FROM customer_order WHERE submit_trace_id IS NOT NULL',True));quantity=int(sql('SELECT COALESCE(SUM(quantity),0) FROM order_item WHERE sku_id=900001',True))
 available=int(subprocess.check_output(['redis-cli','-p',str(stock_port),'--raw','GET','load:'+database+':inventory:v1:{900001}:available'],text=True));pending=int(subprocess.check_output(['redis-cli','-p',str(stock_port),'--raw','GET','load:'+database+':inventory:v1:{900001}:pending'],text=True))
 duplicate_traces=int(sql('SELECT COUNT(*) FROM (SELECT submit_trace_id FROM customer_order WHERE submit_trace_id IS NOT NULL GROUP BY submit_trace_id HAVING COUNT(*)>1) d',True))
 report['outboxStates']=sql("SELECT event_type,status,COUNT(*) FROM order_message_outbox GROUP BY event_type,status",True)
 planned={}
 for phase_file in run.glob('*.json'):
  if phase_file.name.endswith('-input.json'):continue
  for item in json.loads(phase_file.read_text()).get('traces',[]):
   if item.get('plannedEpochMs') is not None:planned.setdefault(item['traceId'],item['plannedEpochMs'])
 completions={}
 completed_rows=[]
 for row in sql("SET time_zone='+08:00'; SELECT trace_id,mode,UNIX_TIMESTAMP(finished_at)*1000 FROM order_submit_trace WHERE status='SUCCESS'",True).splitlines():
  trace,mode,finished=row.split('\t')
  completed_rows.append({'traceId':trace,'mode':mode,'finishedEpochMs':float(finished),'plannedEpochMs':planned.get(trace)})
  if trace in planned:completions.setdefault(mode,[]).append(max(0,float(finished)-planned[trace]))
 def percentiles(values):
  values.sort()
  def percentile(p):return round(values[min(len(values)-1,max(0,__import__('math').ceil(len(values)*p)-1))],2)
  return {'count':len(values),'p50':percentile(.5),'p95':percentile(.95),'p99':percentile(.99),'max':percentile(1)}
 (run/'order-completions.json').write_text(json.dumps(completed_rows,indent=2))
 report['plannedArrivalToOrderCreatedMs']={mode:percentiles(values) for mode,values in completions.items()}
 report['consistency']={'initialStock':20000,'databaseAvailable':stock,'databaseLocked':locked,'redisAvailable':available,'redisPending':pending,'orders':order_count,'orderedQuantity':quantity,'duplicateTraceOrders':duplicate_traces,'traceStates':states};assert stock==20000-quantity and locked==quantity and available==stock and pending==0 and duplicate_traces==0,report['consistency']
 changes=[]
 for row in monitor_rows:
  if not changes or changes[-1]['mode']!=row['mode']:changes.append(row)
 report['modeTransitions']=changes;report['switchAndRecoveryObserved']=any(r['mode']=='ASYNC' for r in changes) and len(changes)>=3 and changes[-1]['mode']=='SYNC'
 assert report['switchAndRecoveryObserved'],'AUTO did not switch and recover; inspect monitor history'
 report['passed']=True
finally:
 monitor_stop.set()
 for p in reversed(processes):stop(p)
 if created:sql('DROP DATABASE `'+database+'`')
 (run/'monitor.json').write_text(json.dumps(monitor_rows,ensure_ascii=False,indent=2));(run/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
 print('REPORT '+str(run/'report.json'),flush=True)
