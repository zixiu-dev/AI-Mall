#!/usr/bin/env python3
"""在仓库 .runtime 内启动独立 PostgreSQL；不启用系统开机服务，不打印密码。"""
import json
import os
from pathlib import Path
import secrets
import shutil
import socket
import subprocess

ROOT=Path(__file__).resolve().parents[1]
STATE=ROOT/".runtime/pgvector"

def main():
    STATE.mkdir(parents=True,exist_ok=True)
    STATE.chmod(0o700)
    binary=Path(os.environ.get("PG_BIN","/opt/homebrew/opt/postgresql@17/bin"))
    if not (binary/"pg_ctl").exists():
        command=shutil.which("pg_ctl")
        if not command:raise SystemExit("请安装 PostgreSQL 17 与 pgvector，或指定 PG_BIN")
        binary=Path(command).parent
    info=STATE/"connection.json"
    if info.exists():
        config=json.loads(info.read_text())
    else:
        with socket.socket() as sock:
            try:sock.bind(("127.0.0.1",5433))
            except OSError:sock.bind(("127.0.0.1",0))
            port=sock.getsockname()[1]
        config={"url":f"jdbc:postgresql://127.0.0.1:{port}/bitmall_knowledge","host":"127.0.0.1","port":port,"database":"bitmall_knowledge","username":"bitmall_local","password":secrets.token_hex(24),"bin":str(binary)}
        fd=os.open(info,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
        with os.fdopen(fd,"w") as file:json.dump(config,file)
    data=STATE/"data"
    env=dict(os.environ,PGPASSWORD=config["password"])
    if not (data/"PG_VERSION").exists():
        password=STATE/"init-password";fd=os.open(password,os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600)
        with os.fdopen(fd,"w") as file:file.write(config["password"]+"\n")
        try:subprocess.run([str(binary/"initdb"),"-D",str(data),"-U",config["username"],"--pwfile="+str(password),"--auth-host=scram-sha-256","--auth-local=scram-sha-256","--encoding=UTF8","--locale=C"],check=True,stdout=subprocess.DEVNULL)
        finally:password.unlink(missing_ok=True)
    status=subprocess.run([str(binary/"pg_ctl"),"-D",str(data),"status"],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    if status.returncode:subprocess.run([str(binary/"pg_ctl"),"-D",str(data),"-l",str(STATE/"postgres.log"),"-o",f"-h 127.0.0.1 -p {config['port']} -k {STATE} -c max_connections=40","-w","start"],check=True,stdout=subprocess.DEVNULL)
    base=[str(binary/"psql"),"-h",config["host"],"-p",str(config["port"]),"-U",config["username"],"-d","postgres","-v","ON_ERROR_STOP=1","-At"]
    present=subprocess.check_output(base+["-c","SELECT 1 FROM pg_database WHERE datname='bitmall_knowledge'"],env=env,text=True).strip()
    if not present:subprocess.run(base+["-c","CREATE DATABASE bitmall_knowledge"],env=env,check=True,stdout=subprocess.DEVNULL)
    subprocess.run([str(binary/"psql"),"-h",config["host"],"-p",str(config["port"]),"-U",config["username"],"-d",config["database"],"-v","ON_ERROR_STOP=1","-c","CREATE EXTENSION IF NOT EXISTS vector"],env=env,check=True,stdout=subprocess.DEVNULL)
    print(json.dumps({"status":"running","host":config["host"],"port":config["port"],"database":config["database"],"privateConfig":".runtime/pgvector/connection.json"}))

if __name__=="__main__":main()
