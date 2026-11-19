-- 旧待支付订单没有 Redis 预占；关单前登记已确认记录，不能再扣一次可售。
local quantity=tonumber(ARGV[1])
if not quantity or quantity<1 or quantity>20 or quantity%1~=0 then return -1 end
local state=redis.call('HGET',KEYS[2],'state')
if state then
 if tonumber(redis.call('HGET',KEYS[2],'quantity'))~=quantity then return -1 end
 return 0
end
if not redis.call('GET',KEYS[1]) then return -1 end
redis.call('HSET',KEYS[2],'quantity',ARGV[1],'state','CONFIRMED');return 1
