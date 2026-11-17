-- 所有实例共用同槽状态。Redis 时间决定采样节拍，多个实例上报也只累计一次连续命中。
local clock=redis.call('TIME')
local now=tonumber(clock[1])*1000+math.floor(tonumber(clock[2])/1000)
local config=cjson.decode(ARGV[3])
local expired=redis.call('ZRANGEBYSCORE',KEYS[1],'-inf',now-config.staleMs)
for _,id in ipairs(expired) do redis.call('ZREM',KEYS[1],id);redis.call('HDEL',KEYS[2],id) end
redis.call('ZADD',KEYS[1],now,ARGV[1]);redis.call('HSET',KEYS[2],ARGV[1],ARGV[2])
local nodes=redis.call('ZRANGE',KEYS[1],0,-1)
local qps,capacity,inflight,permits,p95,samples,db,waiters,pending,age=0,0,0,0,0,0,0,0,0,0
local tps=0
local healthy,asyncReady=true,true
if #nodes>128 then healthy=false end
for _,id in ipairs(nodes) do
 local raw=redis.call('HGET',KEYS[2],id)
 local ok,s=pcall(cjson.decode,raw or '')
 if not ok then healthy=false else
  tps=tps+(tonumber(s.tps) or 0)
  qps=qps+s.qps;capacity=capacity+s.capacity;inflight=inflight+s.inflight;permits=permits+s.permits
  if s.syncSamples>=config.minimumSyncSamples then p95=math.max(p95,s.syncP95Ms);samples=samples+s.syncSamples end
  db=math.max(db,s.databaseUsage);waiters=math.max(waiters,s.databaseWaiters)
  pending=math.max(pending,s.pending);age=math.max(age,s.oldestSeconds)
  if not s.healthy then healthy=false end
  if not s.asyncReady then asyncReady=false end
 end
end
local rate=qps/math.max(capacity,1)
local concurrency=inflight/math.max(permits,1)
local high=rate>=1 or concurrency>=config.inFlightHigh or db>=config.databaseHigh or waiters>0 or p95>=config.p95High
local low=rate<.5 and concurrency<config.inFlightLow and db<config.databaseLow and waiters==0 and p95<config.p95Low and pending<=config.recoverPending
local mode=redis.call('HGET',KEYS[3],'mode') or 'SYNC'
local changed=tonumber(redis.call('HGET',KEYS[3],'changedAt') or '0')
local evaluated=tonumber(redis.call('HGET',KEYS[3],'evaluatedAt') or '0')
local highs=tonumber(redis.call('HGET',KEYS[3],'highSamples') or '0')
local lows=tonumber(redis.call('HGET',KEYS[3],'lowSamples') or '0')
local transition=false
if now-evaluated>=1000 then
 evaluated=now
 highs=(healthy and high) and highs+1 or 0
 lows=(healthy and low) and lows+1 or 0
 if now-changed>=config.cooldownMs then
  if mode=='SYNC' and highs>=config.highSamples then mode='ASYNC';changed=now;transition=true;lows=0
  elseif mode=='ASYNC' and lows>=config.lowSamples then mode='SYNC';changed=now;transition=true;highs=0 end
 end
 redis.call('HSET',KEYS[3],'mode',mode,'changedAt',changed,'evaluatedAt',evaluated,'highSamples',highs,'lowSamples',lows)
end
local accepting=healthy
local reason=high and 'PRESSURE_HIGH' or (low and 'PRESSURE_LOW' or 'HYSTERESIS')
if not healthy then reason='SAMPLING_UNAVAILABLE'
elseif pending>=config.maxPending or age>=config.maxQueueAge then accepting=false;reason='QUEUE_OVERLOADED'
elseif (mode=='ASYNC' or high) and not asyncReady then accepting=false;reason='ASYNC_UNAVAILABLE' end
for _,key in ipairs(KEYS) do redis.call('PEXPIRE',key,config.staleMs*4) end
return cjson.encode({mode=mode,accepting=accepting,reason=reason,changedAt=changed,evaluatedAt=evaluated,highSamples=highs,lowSamples=lows,transition=transition,nodes=#nodes,qps=qps,tps=tps,syncCapacity=capacity,inflightRatio=concurrency,syncP95Ms=p95,databaseUsage=db,databaseWaiters=waiters,pending=pending,oldestSeconds=age})
