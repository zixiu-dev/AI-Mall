-- CONFIRM 建单确认，RELEASE 建单失败释放，RETURN 未付关单返还。
local quantity=tonumber(ARGV[2])
if not quantity or quantity<1 or quantity%1~=0 then return -4 end
local state=redis.call('HGET',KEYS[3],'state')
if not state then
 -- 即使原调用还没执行预扣，失败释放也留下墓碑，阻止迟到调用再扣。
 if ARGV[1]=='RELEASE' then
  redis.call('HSET',KEYS[3],'quantity',quantity,'state','RELEASED');return 0
 end
 return -2
end
if tonumber(redis.call('HGET',KEYS[3],'quantity'))~=quantity then return -5 end
if state=='RELEASED' or state=='RETURNED' then
 if ARGV[1]=='CONFIRM' then return -3 end
 return 0
end
if ARGV[1]=='CONFIRM' and state=='CONFIRMED' then return 0 end
if ARGV[1]=='RELEASE' and state=='CONFIRMED' then return -3 end
local available=tonumber(redis.call('GET',KEYS[1]))
local pending=tonumber(redis.call('GET',KEYS[2]))
if not available or not pending or available<0 or pending<0 or available%1~=0 or pending%1~=0 or available>2147483647 or pending>2147483647 or (state=='RESERVED' and pending<quantity) then return -2 end
if ARGV[1]~='CONFIRM' and available+quantity>2147483647 then return -2 end
if ARGV[1]=='CONFIRM' and state=='RESERVED' then
 redis.call('DECRBY',KEYS[2],quantity)
 redis.call('HSET',KEYS[3],'state','CONFIRMED');return 1
end
if (ARGV[1]=='RELEASE' and state=='RESERVED') or (ARGV[1]=='RETURN' and (state=='RESERVED' or state=='CONFIRMED')) then
 redis.call('INCRBY',KEYS[1],quantity)
 if state=='RESERVED' then redis.call('DECRBY',KEYS[2],quantity) end
 redis.call('HSET',KEYS[3],'state',ARGV[1]=='RETURN' and 'RETURNED' or 'RELEASED');return 1
end
return -3
