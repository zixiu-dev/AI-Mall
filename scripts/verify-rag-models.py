#!/usr/bin/env python3
"""显式连接真实模型，凭据只经环境传给 Maven；报告不包含 Key。"""
import argparse
import json
import os
from pathlib import Path
import shlex
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]

def load_env(path, env):
    for line in path.read_text().splitlines():
        line = line.strip().removeprefix("export ")
        if not line or line.startswith("#") or "=" not in line:
            continue
        name, value = line.split("=", 1)
        name = name.strip()
        if not (name.startswith(("RAG_", "OPENAI_")) or name in ("DASHSCOPE_API_KEY", "SILICONFLOW_API_KEY")):
            continue
        words = shlex.split(value, comments=True)
        env[name] = words[0] if words else ""

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file", type=Path, help="本机私有的 RAG/模型环境文件")
    parser.add_argument("--chat-env-file", type=Path, help="可选：原有网关私有环境文件")
    args = parser.parse_args()
    env = dict(os.environ)
    for path in (args.env_file, args.chat_env_file):
        if path:
            load_env(path, env)
    if not env.get("OPENAI_API_KEY") and env.get("SILICONFLOW_API_KEY"):
        env["OPENAI_API_KEY"] = env["SILICONFLOW_API_KEY"]
        env.setdefault("OPENAI_BASE_URL", "https://api.siliconflow.cn")
        env.setdefault("OPENAI_MODEL", "deepseek-ai/DeepSeek-V4-Flash")
    if not (env.get("RAG_EMBEDDING_API_KEY") or env.get("DASHSCOPE_API_KEY")) or not env.get("RAG_EMBEDDING_BASE_URL") or not env.get("OPENAI_API_KEY"):
        parser.error("请在私有环境配置 embedding 的 Key/base URL 和聊天模型凭据")
    if not env.get("PGVECTOR_TEST_URL"):
        parser.error("请使用 scripts/verify-pgvector.py --real-model 创建独立 PG 测试库，或显式设置 PGVECTOR_TEST_URL/USERNAME/PASSWORD；不允许测试覆盖运行中索引")
    env["RAG_REAL_TESTS"] = "true"
    target = ROOT / ".runtime/rag"
    target.mkdir(parents=True, exist_ok=True)
    with (target / "real-tests.log").open("w") as log:
        result = subprocess.run(["mvn", "-q", "-Dtest=RealRagIntegrationTest", "test"], cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT)
    report = ROOT / "target/surefire-reports/TEST-com.bitmall.agent.knowledge.RealRagIntegrationTest.xml"
    stats = ET.parse(report).getroot().attrib if report.exists() else {}
    summary = {"exitCode": result.returncode, **{k: stats.get(k) for k in ("tests", "failures", "errors", "skipped")}, "log": ".runtime/rag/real-tests.log"}
    (target / "real-summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2))
    print(json.dumps(summary, ensure_ascii=False))
    raise SystemExit(result.returncode)

if __name__ == "__main__":
    main()
