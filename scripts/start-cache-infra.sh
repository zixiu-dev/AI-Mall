#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd "$(dirname "$0")/.." && pwd)"
runtime_dir="$project_dir/.runtime"
mkdir -p "$runtime_dir/logs"
if ! redis-cli -h 127.0.0.1 -p 6380 ping >/dev/null 2>&1; then
  command -v redis-server >/dev/null || { echo 'Please install Redis, or use docker compose up -d redis.' >&2; exit 1; }
  nohup redis-server --bind 127.0.0.1 --port 6380 --save '' --appendonly no --maxmemory 128mb --maxmemory-policy allkeys-lfu \
    > "$runtime_dir/logs/redis.log" 2>&1 < /dev/null &
  echo "$!" > "$runtime_dir/redis.pid"
fi
if ! curl -fsS --max-time 2 http://127.0.0.1:2379/health >/dev/null 2>&1; then
  if [[ "$(uname -s)/$(uname -m)" != 'Darwin/arm64' ]]; then
    echo 'For this platform use docker compose --profile hotkey up -d redis etcd hotkey-worker.' >&2; exit 1
  fi
  etcd_bin="$runtime_dir/etcd-v3.5.21-darwin-arm64/etcd"
  if [[ ! -x "$etcd_bin" ]]; then
    archive="$runtime_dir/etcd.zip"
    curl -fL --retry 2 https://github.com/etcd-io/etcd/releases/download/v3.5.21/etcd-v3.5.21-darwin-arm64.zip -o "$archive"
    printf '%s  %s\n' 'd0ffb98e3671b1de1ca82b1fb6d7548661573519174470a33b6efccacb494f06' "$archive" | shasum -a 256 -c -
    unzip -q "$archive" -d "$runtime_dir"
  fi
  nohup "$etcd_bin" --name wowo-local --data-dir "$runtime_dir/etcd-data" \
    --listen-client-urls http://127.0.0.1:2379 --advertise-client-urls http://127.0.0.1:2379 \
    --listen-peer-urls http://127.0.0.1:2380 --initial-advertise-peer-urls http://127.0.0.1:2380 \
    --initial-cluster wowo-local=http://127.0.0.1:2380 \
    > "$runtime_dir/logs/etcd.log" 2>&1 < /dev/null &
  echo "$!" > "$runtime_dir/etcd.pid"
fi
for attempt in $(seq 1 50); do
  if curl -fsS --max-time 1 http://127.0.0.1:2379/health >/dev/null 2>&1; then break; fi
  sleep 0.2
done
python3 "$project_dir/scripts/init-hotkey.py"
[[ -f "$runtime_dir/hotkey-worker.jar" ]] || bash "$project_dir/scripts/build-hotkey.sh"
if ! nc -z 127.0.0.1 11111 >/dev/null 2>&1; then
  nohup java --add-opens java.base/java.lang=ALL-UNNAMED -jar "$runtime_dir/hotkey-worker.jar" \
    --etcd.server=http://127.0.0.1:2379 --etcd.workerPath=wowo-mall --local.address=127.0.0.1 \
    --server.address=127.0.0.1 --server.port=18081 --thread.count=2 \
    > "$runtime_dir/logs/hotkey-worker.log" 2>&1 < /dev/null &
  echo "$!" > "$runtime_dir/hotkey-worker.pid"
fi
for attempt in $(seq 1 50); do
  if nc -z 127.0.0.1 11111 >/dev/null 2>&1; then
    echo 'Redis :6380 / etcd :2379 / JD HotKey Worker :11111 are ready.'; exit 0
  fi
  sleep 0.2
done
echo "Worker did not start; inspect $runtime_dir/logs/hotkey-worker.log" >&2
exit 1
