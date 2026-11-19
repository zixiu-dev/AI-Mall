-- 仅数据库仍未标记初始化时允许调用；调用方持有 SKU 数据库行锁。
local initial=tonumber(ARGV[1])
if not initial or initial<0 or initial%1~=0 or initial>2147483647 then return -1 end
local available=redis.call('GET',KEYS[1])
if available then
 if tonumber(available)==initial then return 0 end
 return -1
end
redis.call('SET',KEYS[1],ARGV[1])
return 1
