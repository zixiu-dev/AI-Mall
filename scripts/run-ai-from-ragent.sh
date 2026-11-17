#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")/.." && pwd)"
ragent_env_file="${RAGENT_ENV_FILE:-/Users/wowo/nageoffer/ragent/.env.local}"

if [[ ! -f "$ragent_env_file" ]]; then
  echo "找不到 Ragent 环境文件：$ragent_env_file" >&2
  exit 1
fi

api_key=$(awk '/^[[:space:]]*(export[[:space:]]+)?SILICONFLOW_API_KEY=/{sub(/^[^=]*=/, ""); print; exit}' "$ragent_env_file")
if [[ -z "$api_key" ]]; then
  echo "Ragent 环境文件中没有 SILICONFLOW_API_KEY" >&2
  exit 1
fi

export AGENT_AI_ENABLED=true
export OPENAI_API_KEY="$api_key"
export OPENAI_BASE_URL="${OPENAI_BASE_URL:-https://api.siliconflow.cn}"
export OPENAI_MODEL="${OPENAI_MODEL:-deepseek-ai/DeepSeek-V4-Pro}"

if [[ -f "$project_dir/.runtime/order-demo/launch.json" ]]; then
  exec python3 "$project_dir/scripts/start-current-ai.py"
fi
cd "$project_dir"
exec mvn spring-boot:run 
