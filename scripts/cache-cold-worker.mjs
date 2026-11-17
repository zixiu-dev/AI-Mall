// 有限冷缓存冲击：每个 key 同时发出多份请求，验证并发请求被双重检查合并，而非把数据库回源放在预热阶段。
import fs from 'node:fs';
import http from 'node:http';
import {performance} from 'node:perf_hooks';

const config=JSON.parse(fs.readFileSync(process.argv[2],'utf8'));
if(config.origins.some(url=>new URL(url).hostname!=='127.0.0.1'))throw Error('仅允许本轮本机隔离实例');
const agent=new http.Agent({keepAlive:true,maxSockets:config.groups*config.copies});
const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
const codes={},latencies=[],keys=[];let cursor=0,completed=0,valid=0;

function read(id,copy){
  return new Promise(resolve=>{
    const started=performance.now();let finished=false;
    const finish=(code,correct)=>{if(finished)return;finished=true;completed++;valid+=correct?1:0;codes[code]=(codes[code]||0)+1;latencies.push(performance.now()-started);resolve();};
    const url=config.origins[copy%config.origins.length]+'/api/store/products/'+id;
    const req=http.get(url,{agent},res=>{
      let body='';res.on('data',part=>body+=part);res.on('error',()=>finish(0,false));
      res.on('end',()=>{let value;try{value=JSON.parse(body);}catch{}
        // 不仅检查 200，还检查商品 ID、名称和 SKU 归属，防止多个 key 实际返回同一份缓存。
        const correct=res.statusCode===200&&value?.id===id&&value.name.endsWith(' load-'+id)
          &&value.skus.length>0&&value.skus.every(sku=>Math.floor(sku.id/10000)===id);
        finish(res.statusCode,correct);
      });
    });
    req.on('error',()=>finish(0,false));req.setTimeout(15000,()=>req.destroy());
  });
}

fs.writeFileSync(config.ready,'ready');while(!fs.existsSync(config.barrier))await sleep(10);
const start=JSON.parse(fs.readFileSync(config.barrier,'utf8')).epochMs;
while(Date.now()<start)await sleep(Math.min(5,start-Date.now()));
const cpuBefore=process.cpuUsage(),began=performance.now();
await Promise.all(Array.from({length:config.groups},async()=>{
  while(cursor<config.ids.length){
    const id=config.ids[cursor++];
    await Promise.all(Array.from({length:config.copies},(_,copy)=>read(id,copy)));keys.push(id);
  }
}));
latencies.sort((a,b)=>a-b);const cpu=process.cpuUsage(cpuBefore),elapsed=performance.now()-began;
fs.writeFileSync(config.output,JSON.stringify({completed,valid,codes,keys,startEpochMs:start,finishedEpochMs:Date.now(),
  p95Ms:latencies[Math.ceil(latencies.length*.95)-1],p99Ms:latencies[Math.ceil(latencies.length*.99)-1],
  cpuPercent:(cpu.user+cpu.system)/1000/elapsed*100,latencies}));
agent.destroy();
