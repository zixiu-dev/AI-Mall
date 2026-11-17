#!/usr/bin/env bash
set -euo pipefail
# 先 mvn -q -DskipTests package。密钥和连接参数通过环境变量传入，不写命令或日志。
TASK_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
exec java -Dloader.main=com.bitmall.agent.knowledge.config.KnowledgeIndexCommand \
  -cp "$TASK_ROOT/target/wowo-smart-commerce-0.1.0-SNAPSHOT.jar" \
  org.springframework.boot.loader.launch.PropertiesLauncher "$@"
