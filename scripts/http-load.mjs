// 固定到达速率压测器：记录未能发出的请求和计划到达后的总延迟，避免只报告“成功请求很快”。
// 仅供 run-load-test.py 调用；不读取用户账号或模型密钥。
import http from 'node:http';
import fs from 'node:fs';
import {performance} from 'node:perf_hooks';
const config=JSON.parse(fs.readFileSync(process.argv[2],'utf8'));
const base=new URL(config.baseUrl);if(config.monitorUrl&&!['127.0.0.1','localhost'].includes(new URL(config.monitorUrl).hostname))throw Error('监控地址也必须是本机隔离实例');if(!['127.0.0.1','localhost'].includes(base.hostname))throw Error('仅允许压测本机隔离实例');
const agent=new http.Agent({keepAlive:true,maxSockets:config.maxInFlight||64});
function request(path,body){return new Promise(resolve=>{const started=performance.now();const req=http.request(new URL(path,base),{agent,method:body?'POST':'GET',headers:body?{'Content-Type':'application/json'}:{}},res=>{let text='';res.on('data',x=>text+=x);res.on('end',()=>{let data;try{data=JSON.parse(text)}catch{}resolve({status:res.statusCode,data,ms:performance.now()-started});});});req.on('error',()=>resolve({status:0,ms:performance.now()-started}));req.setTimeout(15000,()=>req.destroy());if(body)req.write(JSON.stringify(body));req.end();});}
const total=Math.round(config.rate*config.seconds),tokens=[];
if(config.kind==='order'){
 let index=0;await Promise.all(Array.from({length:8},async()=>{while(index<total){const i=index++;const r=await request('/api/store/order-tokens',{});if(r.status!==200||!r.data.traceId)throw Error('发号失败');tokens[i]=r.data.traceId;}}));
}
const beforeMetrics=config.monitorUrl?(await request(config.monitorUrl)).data:null;
const start=performance.now(),rows=[],pending=new Set();let scheduled=0,dropped=0;
const statuses={},modes={},traces=[];
async function launch(index){
 const planned=start+index*1000/config.rate;let path,body;
 if(config.kind==='order'){path='/api/store/orders/submit';body={userId:10001,traceId:tokens[index],purchase:config.purchase};}
 else if(config.kind==='repeat'){path='/api/store/orders/submit';body=config.repeatBody;}
 else if(config.kind==='missing')path='/api/store/products/'+(7_000_000+index);
 else if(config.kind==='search')path='/api/store/products?query=NovaBook&limit=12';
 else path='/api/store/products/108';
 const result=await request(path,body);statuses[result.status]=(statuses[result.status]||0)+1;
 if(result.data?.mode)modes[result.data.mode]=(modes[result.data.mode]||0)+1;
 if(result.data?.traceId)traces.push({plannedEpochMs:performance.timeOrigin+planned,traceId:result.data.traceId,status:result.data.status,orderId:result.data.orderId,mode:result.data.mode});
 rows.push({status:result.status,httpMs:result.ms,arrivalToResponseMs:performance.now()-planned});
}
await new Promise(resolve=>{const timer=setInterval(()=>{
 const due=Math.min(total,Math.floor((performance.now()-start)*config.rate/1000)+1);
 while(scheduled<due){const index=scheduled++;if(pending.size>=(config.maxInFlight||64)){dropped++;continue;}let task=launch(index).finally(()=>pending.delete(task));pending.add(task);}
 if(scheduled>=total){clearInterval(timer);resolve();}
},5);});
await Promise.all([...pending]);const finished=performance.now(),elapsed=(finished-start)/1000;
const afterMetrics=config.monitorUrl?(await request(config.monitorUrl)).data:null;
if(config.monitorUrl&&(!beforeMetrics||!afterMetrics||typeof beforeMetrics.ordersCreatedTotal!=='number'||typeof afterMetrics.ordersCreatedTotal!=='number'))throw Error('缺少真实建单计数，不能计算 TPS');
const metricsSeconds=beforeMetrics&&afterMetrics?(afterMetrics.sampledAtEpochMs-beforeMetrics.sampledAtEpochMs)/1000:null;
const committedOrders=metricsSeconds?afterMetrics.ordersCreatedTotal-beforeMetrics.ordersCreatedTotal:null;
const values=rows.map(r=>r.arrivalToResponseMs).sort((a,b)=>a-b),httpValues=rows.map(r=>r.httpMs).sort((a,b)=>a-b);
const pct=(a,p)=>a.length?+a[Math.min(a.length-1,Math.ceil(a.length*p)-1)].toFixed(2):0;
const expected=config.kind==='missing'?400:200;const success=rows.filter(r=>r.status===expected).length;
const result={startedEpochMs:performance.timeOrigin+start,finishedEpochMs:performance.timeOrigin+finished,measuredIntervalSeconds:metricsSeconds,ordersCommittedDuringInterval:committedOrders,businessTps:metricsSeconds?+(committedOrders/metricsSeconds).toFixed(2):null,qps:+(rows.length/elapsed).toFixed(2),name:config.name,kind:config.kind,offeredRate:config.rate,durationSeconds:config.seconds,offered:total,clientDropped:dropped,completed:rows.length,expectedResponses:success,errorResponses:rows.length-success,elapsedSeconds:+elapsed.toFixed(3),completedRps:+(rows.length/elapsed).toFixed(2),expectedRps:+(success/elapsed).toFixed(2),arrivalLatencyMs:{p50:pct(values,.5),p95:pct(values,.95),p99:pct(values,.99),max:pct(values,1)},httpLatencyMs:{p50:pct(httpValues,.5),p95:pct(httpValues,.95),p99:pct(httpValues,.99)},statuses,modes,traces};
fs.writeFileSync(config.output,JSON.stringify(result,null,2));console.log(JSON.stringify({...result,traces:undefined}));agent.destroy();
