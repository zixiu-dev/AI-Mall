#!/usr/bin/env python3
"""Read-only load to verify the REAL JD Worker -> client -> Caffeine path, not a QPS benchmark."""
import concurrent.futures
import json
import os
import time
import urllib.request

store = os.getenv("STORE_URL", "http://127.0.0.1:8080")
management = os.getenv("MANAGEMENT_URL", "http://127.0.0.1:9080")
def read(path):
    with urllib.request.urlopen(path, timeout=5) as response:
        return json.load(response)
before = read(management + "/actuator/catalogcache")
if not before["hotkey"]["workerConnected"]:
    raise SystemExit("JD Worker not connected; start infrastructure and set HOTKEY_ENABLED=true first.")
for _ in range(5):
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        list(pool.map(lambda _: read(store + "/api/store/products/108"), range(60)))
    time.sleep(0.6)
after = read(management + "/actuator/catalogcache")
delta = {key: value-before["counters"].get(key, 0) for key, value in after["counters"].items()}
print(json.dumps({"hotkey": after["hotkey"], "counterDelta": delta}, ensure_ascii=False, indent=2))
if delta.get("local_hit", 0) <= 0:
    raise SystemExit("No Caffeine hits observed; inspect Worker rules, transport and application logs.")
