-- 只在压测结束时输出结构化结果；不执行逐请求 Lua 回调，避免发压器成为 JSON 解析瓶颈。
done = function(summary, latency, requests)
  io.write(string.format('PEAK_JSON {"requests":%d,"durationMicros":%d,"statusErrors":%d,"connectErrors":%d,"readErrors":%d,"writeErrors":%d,"timeouts":%d,"p50us":%.3f,"p95us":%.3f,"p99us":%.3f,"maxus":%.3f}\n',
    summary.requests, summary.duration, summary.errors.status, summary.errors.connect,
    summary.errors.read, summary.errors.write, summary.errors.timeout,
    latency:percentile(50), latency:percentile(95), latency:percentile(99), latency.max))
end
