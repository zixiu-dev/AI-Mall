-- 预生成不同商品的 HTTP 请求，按互质步长打散轮转，避免在逐请求回调中拼接 URL/解析 JSON。
local workers = {}
function setup(thread)
  workers[#workers + 1] = thread
  thread:set("slot", #workers)
end

function init(args)
  first_id = tonumber(args[1])
  key_count = tonumber(args[2])
  local function gcd(a, b) while b ~= 0 do a, b = b, a % b end return a end
  step = 73
  while gcd(step, key_count) ~= 1 do step = step + 1 end
  requests_by_key, prepared = {}, {}
  for i = 1, key_count do
    prepared[i] = wrk.format("GET", "/api/store/products/" .. (first_id + i - 1))
    requests_by_key[i] = 0
  end
  position = ((slot or 1) * 127) % key_count
end

function request()
  position = (position + step) % key_count
  local index = position + 1
  requests_by_key[index] = requests_by_key[index] + 1
  return prepared[index]
end

function done(summary, latency, requests)
  local totals, distinct, minimum, maximum, issued = {}, 0, math.huge, 0, 0
  for _, thread in ipairs(workers) do
    for key, count in pairs(thread:get("requests_by_key")) do totals[key] = (totals[key] or 0) + count end
  end
  for _, count in pairs(totals) do
    if count > 0 then distinct = distinct + 1 end
    minimum, maximum, issued = math.min(minimum, count), math.max(maximum, count), issued + count
  end
  io.write(string.format('PEAK_JSON {"requests":%d,"durationMicros":%d,"statusErrors":%d,"connectErrors":%d,"readErrors":%d,"writeErrors":%d,"timeouts":%d,"p50us":%.3f,"p95us":%.3f,"p99us":%.3f,"distinctKeysRequested":%d,"minIssuedPerKey":%d,"maxIssuedPerKey":%d,"issued":%d}\n',
    summary.requests, summary.duration, summary.errors.status, summary.errors.connect,
    summary.errors.read, summary.errors.write, summary.errors.timeout, latency:percentile(50),
    latency:percentile(95), latency:percentile(99), distinct, minimum, maximum, issued))
end
