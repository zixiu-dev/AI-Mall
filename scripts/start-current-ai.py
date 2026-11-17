#!/usr/bin/env python3
"""使用当前本地商城配置连接真实模型；凭证仅保留在进程环境中。先停止已有商城进程再运行。"""
import json
import os
from pathlib import Path
import shlex

root = Path(__file__).resolve().parents[1]
config = root / ".runtime/order-demo/launch.json"
if not config.exists():
    raise SystemExit("未发现现有商城启动配置，请使用 scripts/run-ai-from-ragent.sh")
env = os.environ.copy()
if not env.get("OPENAI_API_KEY"):
    source = Path(env.get("RAGENT_ENV_FILE", "/Users/wowo/nageoffer/ragent/.env.local"))
    for line in source.read_text().splitlines():
        line = line.strip().removeprefix("export ")
        if line.startswith("SILICONFLOW_API_KEY="):
            values = shlex.split(line.split("=", 1)[1], comments=True)
            if values:
                env["OPENAI_API_KEY"] = values[0]
            break
if not env.get("OPENAI_API_KEY"):
    raise SystemExit("没有可用模型密钥，拒绝回退到演示模式")
env["AGENT_AI_ENABLED"] = "true"
env.setdefault("OPENAI_BASE_URL", "https://api.siliconflow.cn")
env.setdefault("OPENAI_MODEL", "deepseek-ai/DeepSeek-V4-Flash")
options = [x for x in json.loads(config.read_text())["applicationOptions"]
           if not x.startswith(("--app.agent.ai-enabled=", "--app.order-processing.demo-activity-enabled="))]
options.append("--app.agent.ai-enabled=true")
mysql_file = root / ".runtime/mysql/connection.json"
if any(x.startswith("--spring.datasource.url=jdbc:mysql:") for x in options) and mysql_file.exists():
    mysql = json.loads(mysql_file.read_text())
    env.setdefault("DB_USERNAME", mysql["username"])
    env.setdefault("DB_PASSWORD", mysql["password"])

jars = list((root / "target").glob("*.jar"))
if len(jars) != 1:
    raise SystemExit("请先 mvn package，确保 target 下只有当前应用 JAR")
os.chdir(root)
os.execvpe("java", ["java", "-jar", str(jars[0]), *options], env)
