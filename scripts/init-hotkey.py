#!/usr/bin/env python3
"""Create/update only this application's rules via the official etcd v3 gateway."""
import base64
import json
import os
import urllib.request

endpoint = os.getenv("HOTKEY_ETCD_SERVER", "http://127.0.0.1:2379").split(",")[0].rstrip("/")
app = os.getenv("HOTKEY_APP_NAME", "wowo-mall")
rules = [
    {"key": "mall:product:", "prefix": True, "interval": 2, "threshold": 20,
     "duration": 30, "desc": "商品详情：2 秒内 20 次，热点标记保留 30 秒"},
    {"key": "mall:search:", "prefix": True, "interval": 2, "threshold": 20,
     "duration": 30, "desc": "相同查询：2 秒内 20 次，热点标记保留 30 秒"},
]
payload = json.dumps({
    "key": base64.b64encode(f"/jd/rules/{app}".encode()).decode(),
    "value": base64.b64encode(json.dumps(rules, ensure_ascii=False).encode()).decode(),
}).encode()
request = urllib.request.Request(endpoint + "/v3/kv/put", payload, {"Content-Type": "application/json"})
with urllib.request.urlopen(request, timeout=5) as response:
    response.read()
print(f"HotKey rules initialized for {app}: 20 requests / 2 seconds")
