-- 同一 SKU 的键同槽；所有验证在写操作之前完成。
local quantity=tonumber(ARGV[1])
if not quantity or quantity<1 or quantity>20 or quantity%1~=0 then return -4 end
local state=redis.call('HGET',KEYS[3],'state')
if state then
 if tonumber(redis.call('HGET',KEYS[3],'quantity'))~=quantity then return -5 end
 if state=='RESERVED' or state=='CONFIRMED' then return 2 end
 return -3
end
local available=tonumber(redis.call('GET',KEYS[1]))
local pending=tonumber(redis.call('GET',KEYS[2]))
if not available or not pending or available<0 or pending<0 or available%1~=0 or pending%1~=0 or available>2147483647 or pending+quantity>2147483647 then return -2 end
if available<quantity then return 0 end
redis.call('DECRBY',KEYS[1],quantity)
redis.call('INCRBY',KEYS[2],quantity)
redis.call('HSET',KEYS[3],'quantity',quantity,'state','RESERVED')
return 1
