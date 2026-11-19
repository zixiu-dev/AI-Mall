#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""峰值测试专用的隔离基础设施；只删除自己创建的随机数据库和进程。"""
import json,os,re,socket,subprocess,time,urllib.parse,urllib.request,uuid
from pathlib import Path

class BenchmarkEnvironment:
    def __init__(self, root, folder, rocketmq):
        self.root=Path(root);self.folder=Path(folder);self.folder.mkdir(parents=True,exist_ok=True)
        self.rocketmq=Path(rocketmq).resolve();assert (self.rocketmq/'lib').is_dir()
        self.database='bitmall_peak_'+uuid.uuid4().hex[:12];self.created=False;self.processes=[];self.app=None;self.grants=[]
        self.metadata=json.loads((self.root/'.runtime/mysql/connection.json').read_text())
        self.mysql=['/usr/local/mysql/bin/mysql','--defaults-extra-file='+str(self.root/'.runtime/mysql/root.cnf'),'-N','-B']
        used=set()
        while len(used)<5:used.add(self.free_port())
        self.app_port,self.management_port,self.cache_port,self.stock_port,self.nameserver_port=used
        while True:
            candidate=self.free_port();holders=[]
            if {candidate-2,candidate,candidate+1}&used:continue
            try:
                for value in [candidate-2,candidate,candidate+1]:
                    s=socket.socket();s.bind(('127.0.0.1',value));holders.append(s)
                self.broker_port=candidate;break
            except OSError:pass
            finally:
                for s in holders:s.close()
        self.ports={k:getattr(self,k+'_port') for k in ['app','management','cache','stock','nameserver','broker']}
        (self.folder/'ports.json').write_text(json.dumps(self.ports))
    @staticmethod
    def free_port():
        with socket.socket() as s:s.bind(('127.0.0.1',0));return s.getsockname()[1]
    def sql(self, query, database=True):
        return subprocess.check_output(self.mysql+(['--database='+self.database] if database else [])+['-e',query],text=True).strip()
    def launch(self, command, name, env=None):
        log=(self.folder/(name+'.log')).open('a');p=subprocess.Popen(command,cwd=self.root,env=env,stdin=subprocess.DEVNULL,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
        self.processes.append((name,p));(self.folder/'processes.json').write_text(json.dumps({n:p.pid for n,p in self.processes}));return p
    @staticmethod
    def stop(process):
        if process is not None and process.poll() is None:
            process.terminate()
            try:process.wait(timeout=12)
            except subprocess.TimeoutExpired:process.kill();process.wait(timeout=5)
    def wait_port(self, port, process):
        until=time.monotonic()+40
        while time.monotonic()<until:
            if process.poll() is not None:raise RuntimeError('基础设施退出，请检查 '+str(self.folder))
            try:
                with socket.create_connection(('127.0.0.1',port),timeout=.2):return
            except OSError:time.sleep(.2)
        raise RuntimeError('端口未就绪 '+str(port))
    def api(self, path, management=False, body=None, timeout=10):
        url='http://127.0.0.1:'+str(self.management_port if management else self.app_port)+path
        request=urllib.request.Request(url,data=None if body is None else json.dumps(body).encode(),headers={'Content-Type':'application/json'})
        with urllib.request.urlopen(request,timeout=timeout) as r:return json.load(r)
    def start(self, broker_overrides=None):
        self.sql('CREATE DATABASE `'+self.database+'` CHARACTER SET utf8mb4 COLLATE utf8mb4_bin',False);self.created=True
        user=self.metadata['username'];assert re.fullmatch(r'[a-zA-Z0-9_]+',user)
        for host in self.sql("SELECT Host FROM mysql.user WHERE User='"+user+"'",False).splitlines():
            assert re.fullmatch(r'[a-zA-Z0-9._%:-]+',host)
            self.sql("GRANT ALL PRIVILEGES ON `"+self.database+"`.* TO '"+user+"'@'"+host+"'",False);self.grants.append((user,host))
        for name,port in [('cache',self.cache_port),('stock',self.stock_port)]:
            directory=self.folder/name;directory.mkdir()
            p=self.launch(['redis-server','--bind','127.0.0.1','--port',str(port),'--save','','--appendonly','no','--maxmemory-policy','noeviction','--dir',str(directory)],name);self.wait_port(port,p)
        home=self.folder/'mq-home';home.mkdir();classpath=str(self.rocketmq/'lib'/'*')
        common=['java','-Duser.home='+str(home),'-Drocketmq.home.dir='+str(self.rocketmq),'--add-opens=java.base/java.nio=ALL-UNNAMED','--add-opens=java.base/sun.nio.ch=ALL-UNNAMED']
        config=self.folder/'namesrv.conf';config.write_text('listenPort='+str(self.nameserver_port)+'\nbindAddress=127.0.0.1\n')
        p=self.launch(common+['-Xms64m','-Xmx128m','-cp',classpath,'org.apache.rocketmq.namesrv.NamesrvStartup','-c',str(config)],'nameserver');self.wait_port(self.nameserver_port,p)
        # Broker 名称会被注册为系统 Topic，业务 Topic 需要使用另一名称。
        config=self.folder/'broker.conf';settings=['brokerClusterName=IsolatedPeak','brokerName='+self.database+'_broker','brokerId=0','brokerIP1=127.0.0.1','bindAddress=127.0.0.1','listenPort='+str(self.broker_port),'namesrvAddr=127.0.0.1:'+str(self.nameserver_port),'storePathRootDir='+str(self.folder/'store'),'storePathCommitLog='+str(self.folder/'store'/'commitlog'),'brokerRole=ASYNC_MASTER','flushDiskType=ASYNC_FLUSH','timerWheelEnable=true','autoCreateTopicEnable=true','mappedFileSizeCommitLog=16777216','mappedFileSizeConsumeQueue=600000','diskMaxUsedSpaceRatio=95']
        if broker_overrides:settings.extend(f'{key}={value}' for key,value in broker_overrides.items())
        config.write_text('\n'.join(settings))
        p=self.launch(common+['-Xms128m','-Xmx384m','-XX:MaxDirectMemorySize=128m','-cp',classpath,'org.apache.rocketmq.broker.BrokerStartup','-c',str(config)],'broker');self.wait_port(self.broker_port,p)
        admin=common+['-Xms32m','-Xmx128m','-cp',classpath,'org.apache.rocketmq.tools.command.MQAdminStartup']
        route_options=['-n','127.0.0.1:'+str(self.nameserver_port),'-t',self.database]
        topic_setup=subprocess.check_output(admin+['updateTopic',*route_options,'-b','127.0.0.1:'+str(self.broker_port),'-r','4','-w','4'],timeout=30,text=True,stderr=subprocess.STDOUT)
        (self.folder/'mq-admin.log').write_text(topic_setup)
        if 'create topic to ' not in topic_setup.lower() or 'success' not in topic_setup.lower():
            raise RuntimeError('隔离业务 Topic 创建失败，请检查 '+str(self.folder/'mq-admin.log'))
        # Broker 刚启动时接受建 Topic 请求可能早于 NameServer 可查到路由。
        until=time.monotonic()+35
        while time.monotonic()<until:
            route=subprocess.check_output(admin+['topicRoute',*route_options],timeout=10,text=True,stderr=subprocess.STDOUT)
            if '"queueDatas"' in route and self.database+'_broker' in route:return
            time.sleep(.5)
        raise RuntimeError('隔离业务 Topic 路由未就绪，请检查 '+str(self.folder))
    def start_app(self, mode='SYNC', local_mode='OFF', one_second=False, overrides=None, replace=True):
        if replace:self.stop(self.app)
        env={k:v for k,v in os.environ.items() if not k.startswith(('SPRING_','ROCKETMQ_','ORDER_','CATALOG_','HOTKEY_','AGENT_','OPENAI_','MANAGEMENT_','SERVER_','LOGGING_'))}
        for key in ['JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','REDISCLI_AUTH']:env.pop(key,None)
        env.update(DB_USERNAME=self.metadata['username'],DB_PASSWORD=self.metadata['password'],AGENT_AI_ENABLED='false',HOTKEY_ENABLED='false',ROCKETMQ_ACCESS_KEY='',ROCKETMQ_SECRET_KEY='',ORDER_NAMESPACE='peak:'+self.database+':',CATALOG_CACHE_NAMESPACE='peak:'+self.database+':catalog')
        jdbc=re.sub(r'(/)[^/?]+(?=\?|$)',r'\g<1>'+self.database,self.metadata['url'],count=1)
        jars=list((self.root/'target').glob('*.jar'));assert len(jars)==1
        config={'spring.profiles.active':'peak','spring.datasource.url':jdbc,'spring.datasource.hikari.maximum-pool-size':16,'spring.data.redis.host':'127.0.0.1','spring.data.redis.port':self.cache_port,'app.inventory.enabled':'true','app.order-processing.redis-port':self.stock_port,'app.order-processing.locks-enabled':'true','app.order-processing.max-concurrent':32,'app.order-processing.submission-mode':mode,'app.order-messaging.enabled':'true','app.order-messaging.topic':self.database,'app.order-messaging.consumer-group':self.database+'_events','app.order-messaging.build-consumer-group':self.database+'_build','app.order-messaging.timeout-consumer-group':self.database+'_timeout','rocketmq.producer.group':self.database+'_producer','rocketmq.name-server':'127.0.0.1:'+str(self.nameserver_port),'server.address':'127.0.0.1','server.port':self.app_port,'management.server.address':'127.0.0.1','management.server.port':self.management_port,'management.endpoints.web.exposure.include':'health,metrics,ordermonitor,catalogcache','app.catalog-cache.enabled':'true','app.catalog-cache.detail-ttl':'300s','app.catalog-cache.local-ttl':'120s','app.catalog-local.mode':local_mode,'app.agent.ai-enabled':'false','app.hotkey.enabled':'false','logging.level.root':'WARN','logging.level.org.springframework':'WARN','logging.level.com.bitmall.agent':'INFO','debug':'false','trace':'false'}
        if one_second:
            config['app.throughput.enabled']='true'
            config['management.endpoints.web.exposure.include']+=',throughput'
        # 多 key 压测可显式调整 TTL；双实例验证通过 replace=False 共享本轮私有 DB/Redis。
        if overrides:config.update(overrides)
        self.app=self.launch(['java','-Xms128m','-Xmx512m','-jar',str(jars[0]),*['--'+k+'='+str(v) for k,v in config.items()]],'application',env)
        until=time.monotonic()+60
        while time.monotonic()<until:
            if self.app.poll() is not None:raise RuntimeError('应用退出 '+str(self.folder/'application.log'))
            try:
                state=self.api('/actuator/ordermonitor',True)
                if 'ordersCreatedTotal' in state:
                    assert self.api('/actuator/catalogcache',True)['localMode']==local_mode;return
            except (OSError,ValueError):pass
            time.sleep(.3)
        raise RuntimeError('应用未就绪')
    def outstanding(self):
        query="SELECT COUNT(*) FROM order_submit_trace WHERE status='PROCESSING' OR reserve_status IN ('RESERVING','RESERVED','CONFIRM_PENDING','RELEASE_PENDING','RETURN_PENDING')"
        return int(self.sql(query))
    def drain(self, timeout=600, output=None):
        """等已受理交易和库存整理到终态，逐秒落盘；Outbox 余量另行记录，绝不隐藏超时。"""
        began=time.monotonic();samples=[]
        while True:
            count=self.outstanding();metrics=self.api('/actuator/ordermonitor',True)
            samples.append({'epochMs':time.time()*1000,'pending':count,'created':metrics['ordersCreatedTotal'],'tps':metrics['tps'],'states':self.sql("SELECT status,reserve_status,COUNT(*) FROM order_submit_trace GROUP BY status,reserve_status"),'outbox':self.sql("SELECT event_type,status,COUNT(*) FROM order_message_outbox GROUP BY event_type,status")})
            result={'seconds':time.monotonic()-began,'samples':samples,'drained':count==0}
            if output:Path(output).write_text(json.dumps(result,indent=2))
            if count==0:return result
            if len(samples)%10==0:print('DRAIN '+json.dumps(samples[-1]),flush=True)
            if time.monotonic()-began>timeout:raise RuntimeError('异步任务未及时清空；状态已保存在 '+str(output))
            time.sleep(1)
    def consistency(self):
        unfinished=self.outstanding()
        unfinished_returns=int(self.sql("SELECT COUNT(*) FROM order_inventory_return WHERE completed=FALSE"))
        assert unfinished==0 and unfinished_returns==0,(unfinished,unfinished_returns)
        rows=self.sql("SELECT id,stock,locked_stock,redis_inventory_initialized FROM product_sku WHERE id>=900001 AND id<900065 ORDER BY id")
        available=locked=0;detail=[]
        for row in rows.splitlines():
            if not row:continue
            sku,stock,hold,initialized=map(int,row.split('\t'))
            key='peak:'+self.database+':inventory:v1:{'+str(sku)+'}:available'
            output=subprocess.check_output(['redis-cli','-p',str(self.stock_port),'--raw','GET',key],text=True).strip()
            redis_stock=int(output) if output else None
            expected_redis_stock=stock if initialized else None
            assert redis_stock==expected_redis_stock,(sku,redis_stock,expected_redis_stock)
            available+=stock;locked+=hold;detail.append({'sku':sku,'stock':stock,'locked':hold,'redisAvailable':redis_stock})
        quantity=int(self.sql('SELECT COALESCE(SUM(quantity),0) FROM order_item WHERE sku_id>=900001 AND sku_id<900065'))
        duplicate=int(self.sql('SELECT COUNT(*) FROM (SELECT submit_trace_id FROM customer_order WHERE submit_trace_id IS NOT NULL GROUP BY submit_trace_id HAVING COUNT(*)>1) d'))
        assert available+locked==len(detail)*5_000_000 and locked==quantity and duplicate==0
        return {'skuCount':len(detail),'orderedQuantity':quantity,'databaseAvailable':available,'databaseLocked':locked,'unfinishedTraces':unfinished,'unfinishedReturns':unfinished_returns,'duplicateOrders':duplicate,'skus':detail,'traceStates':self.sql('SELECT status,COUNT(*) FROM order_submit_trace GROUP BY status'),'outboxStates':self.sql('SELECT event_type,status,COUNT(*) FROM order_message_outbox GROUP BY event_type,status')}
    def close(self):
        for _,p in reversed(self.processes):self.stop(p)
        if self.created:
            self.sql('DROP DATABASE `'+self.database+'`',False)
            for user,host in self.grants:self.sql("REVOKE ALL PRIVILEGES ON `"+self.database+"`.* FROM '"+user+"'@'"+host+"'",False)
            self.created=False
