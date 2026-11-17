-- 仅数据库仍未标记初始化时允许调用；调用方持有 SKU 数据库行锁。
local initial=tonumber(ARGV[1])
if not initial or initial<0 or initial%1~=0 or initial>2147483647 then return -1 end
local a=redis.call('GET',KEYS[1])
local p=redis.call('GET',KEYS[2])
if a or p then
 if tonumber(a)==tonumber(ARGV[1]) and tonumber(p)==0 then return 0 end
 return -1
end
redis.call('SET',KEYS[1],ARGV[1]);redis.call('SET',KEYS[2],0);return 1
