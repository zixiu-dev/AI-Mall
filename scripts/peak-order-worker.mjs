// 多进程订单发压子进程。预先通过后端取得 trace；统一屏障启动，SIGUSR1 停止新请求并等待已发请求。
import http from 'node:http';
import fs from 'node:fs';
import {performance,monitorEventLoopDelay} from 'node:perf_hooks';
const cfg=JSON.parse(fs.readFileSync(process.argv[2],'utf8'));
const origin=new URL(cfg.baseUrl);if(origin.hostname!=='127.0.0.1')throw Error('只能向隔离本机实例发压');
const agent=new http.Agent({keepAlive:true,maxSockets:cfg.inflight});
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
function request(path,body){return new Promise(resolve=>{let done=false;const finish=x=>{if(!done){done=true;resolve(x)}};const started=performance.now();const req=http.request(new URL(path,origin),{agent,method:'POST',headers:{'Content-Type':'application/json'}},res=>{let text='';res.on('data',d=>text+=d);res.on('end',()=>{let data;try{data=JSON.parse(text)}catch{}finish({code:res.statusCode,data,ms:performance.now()-started})});res.on('error',()=>finish({code:0,ms:performance.now()-started}));});req.on('error',()=>finish({code:0,ms:performance.now()-started}));req.setTimeout(12000,()=>req.destroy());req.end(JSON.stringify(body));});}
const total=Math.ceil(cfg.rate*cfg.seconds),tokens=new Array(total);let prepared=0;
await Promise.all(Array.from({length:12},async()=>{while(prepared<total){const i=prepared++;const r=await request('/api/store/order-tokens',{});if(r.code!==200||!r.data?.traceId)throw Error('发号失败');tokens[i]=r.data.traceId;}}));
fs.writeFileSync(cfg.ready,'ready');while(!fs.existsSync(cfg.start))await sleep(10);
const startEpoch=JSON.parse(fs.readFileSync(cfg.start,'utf8')).epochMs;while(Date.now()<startEpoch)await sleep(Math.min(5,startEpoch-Date.now()));
const zero=performance.now()-(Date.now()-startEpoch),cpuBefore=process.cpuUsage(),eventLoop=monitorEventLoopDelay({resolution:10});eventLoop.enable();
const histogram=()=>new Uint32Array(800),httpHistogram=histogram(),arrivalHistogram=histogram();
function add(hist,ms){hist[Math.min(799,Math.max(0,Math.ceil(Math.log1p(Math.max(0,ms))/Math.log(1.02))))]++;}
let sent=0,dropped=0,completed=0,accepted=0,halt=false;const pending=new Set(),codes={},modes={},traces=[];
process.on('SIGUSR1',()=>halt=true);process.on('SIGINT',()=>halt=true);
async function send(index){const planned=zero+index*1000/cfg.rate;const purchase=cfg.purchases[index%cfg.purchases.length];const submitEpochMs=performance.timeOrigin+performance.now();const r=await request('/api/store/orders/submit',{userId:10001,traceId:tokens[index],purchase});completed++;if(r.code===200&&['SUCCESS','PROCESSING'].includes(r.data?.status))accepted++;codes[r.code]=(codes[r.code]||0)+1;if(r.data?.mode)modes[r.data.mode]=(modes[r.data.mode]||0)+1;add(httpHistogram,r.ms);add(arrivalHistogram,performance.now()-planned);traces.push({traceId:tokens[index],plannedEpochMs:startEpoch+index*1000/cfg.rate,submitEpochMs,httpMs:r.ms,httpCode:r.code,status:r.data?.status,mode:r.data?.mode});}
await new Promise(resolve=>{const timer=setInterval(()=>{if(halt){clearInterval(timer);resolve();return;}const due=Math.min(total,Math.floor((performance.now()-zero)*cfg.rate/1000)+1);while(sent<due){const index=sent++;if(pending.size>=cfg.inflight){dropped++;continue;}let task=send(index).finally(()=>pending.delete(task));pending.add(task);}if(sent>=total){clearInterval(timer);resolve();}},2);});
await Promise.all([...pending]);const finish=performance.now(),cpu=process.cpuUsage(cpuBefore);eventLoop.disable();
fs.writeFileSync(cfg.output,JSON.stringify({startEpochMs:startEpoch,finishedEpochMs:performance.timeOrigin+finish,offered:total,scheduled:sent,dropped,completed,accepted,codes,modes,httpHistogram:Array.from(httpHistogram),arrivalHistogram:Array.from(arrivalHistogram),cpuPercent:(cpu.user+cpu.system)/1000/(finish-zero)*100,eventLoopP99Ms:eventLoop.percentile(99)/1e6,halted:halt,traces}));agent.destroy();
