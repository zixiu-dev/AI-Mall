#!/usr/bin/env python3
"""建立独立 PG 测试数据库，执行真实向量库验证后清理；不输出私有凭据。"""
from pathlib import Path
import argparse,json,os,subprocess,uuid
ROOT=Path(__file__).resolve().parents[1]
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument("--full",action="store_true",help="完整回归；MySQL 需通过 MYSQL_TEST_* 指向隔离库");parser.add_argument("--real-model",action="store_true",help="额外运行已配置的真实模型验证");args=parser.parse_args()
    info=json.loads((ROOT/".runtime/pgvector/connection.json").read_text());name="bitmall_pgtest_"+uuid.uuid4().hex[:10];env=dict(os.environ,PGPASSWORD=info["password"])
    psql=[info["bin"]+"/psql","-h",info["host"],"-p",str(info["port"]),"-U",info["username"],"-d","postgres","-v","ON_ERROR_STOP=1","-At"]
    def sql(query):subprocess.run(psql+["-c",query],env=env,check=True,stdout=subprocess.DEVNULL)
    sql('CREATE DATABASE "'+name+'"')
    env.update(PGVECTOR_TEST_URL=f"jdbc:postgresql://{info['host']}:{info['port']}/{name}",PGVECTOR_TEST_USERNAME=info["username"],PGVECTOR_TEST_PASSWORD=info["password"])
    env.update(RAG_PG_URL=env["PGVECTOR_TEST_URL"],RAG_PG_USERNAME=info["username"],RAG_PG_PASSWORD=info["password"])
    try:
        if args.real_model:
            import importlib.util
            spec=importlib.util.spec_from_file_location("verify_rag",ROOT/"scripts/verify-rag-models.py");module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module);
            provider=ROOT/".runtime/rag/provider.env"
            if provider.exists():module.load_env(provider,env)
            chat_file=Path(env.get("RAGENT_ENV_FILE","/Users/wowo/nageoffer/ragent/.env.local"))
            if chat_file.exists():module.load_env(chat_file,env)
            env.setdefault("OPENAI_API_KEY",env.get("SILICONFLOW_API_KEY",""));env.setdefault("OPENAI_BASE_URL","https://api.siliconflow.cn");env.setdefault("OPENAI_MODEL","deepseek-ai/DeepSeek-V4-Flash");env["RAG_REAL_TESTS"]="true"
        env.update(RAG_PG_URL=env["PGVECTOR_TEST_URL"],RAG_PG_USERNAME=info["username"],RAG_PG_PASSWORD=info["password"])
        command=["mvn","-q","test"] if args.full else ["mvn","-q","-Dtest="+("PgVectorIndexIntegrationTest,RealRagIntegrationTest" if args.real_model else "PgVectorIndexIntegrationTest"),"test"]
        log=ROOT/".runtime/pgvector"/("full-tests.log" if args.full else "real-tests.log" if args.real_model else "tests.log")
        with log.open("w") as file:result=subprocess.run(command,cwd=ROOT,env=env,stdout=file,stderr=subprocess.STDOUT)
        import xml.etree.ElementTree as ET
        reports=list((ROOT/"target/surefire-reports").glob("TEST-*.xml")) if args.full else [ROOT/"target/surefire-reports/TEST-com.bitmall.agent.knowledge.PgVectorIndexIntegrationTest.xml"]+([ROOT/"target/surefire-reports/TEST-com.bitmall.agent.knowledge.RealRagIntegrationTest.xml"] if args.real_model else [])
        summary={key:0 for key in ("tests","failures","errors","skipped")}
        for report in reports:
            if not report.exists():continue
            data=ET.parse(report).getroot().attrib
            for key in summary:summary[key]+=int(data.get(key,0))
        summary.update(exitCode=result.returncode,log=str(log.relative_to(ROOT)))
        log.with_name(log.stem+"-summary.json").write_text(json.dumps(summary,ensure_ascii=False,indent=2))
        print(json.dumps(summary));raise SystemExit(result.returncode)
    finally:sql('DROP DATABASE "'+name+'" WITH (FORCE)')
if __name__=="__main__":main()
