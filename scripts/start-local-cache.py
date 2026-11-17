#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""启动本机商品缓存 Redis（6380），与交易库存 Redis（6381）分离；缓存可淘汰、可重建。"""
import json,os,subprocess,time
from pathlib import Path

root=Path(__file__).resolve().parents[1];directory=root/'.runtime/cache-redis';directory.mkdir(parents=True,exist_ok=True)
env=os.environ.copy();env.pop('REDISCLI_AUTH',None)
def ping():
 try:return subprocess.run(['redis-cli','-h','127.0.0.1','-p','6380','PING'],env=env,capture_output=True,text=True,timeout=1).stdout.strip()=='PONG'
 except (OSError,subprocess.TimeoutExpired):return False
if ping():print('商品缓存 Redis 已运行：127.0.0.1:6380');raise SystemExit(0)
log=(directory/'redis.log').open('a')
process=subprocess.Popen(['redis-server','--bind','127.0.0.1','--port','6380','--save','','--appendonly','no','--maxmemory','128mb','--maxmemory-policy','allkeys-lru','--dir',str(directory)],stdout=log,stderr=subprocess.STDOUT,start_new_session=True,env=env)
for _ in range(50):
 if process.poll() is not None:raise SystemExit('Redis 启动失败，请检查 '+str(directory/'redis.log'))
 if ping():
  (directory/'redis.pid').write_text(str(process.pid));print(json.dumps({'pid':process.pid,'port':6380,'policy':'allkeys-lru','maxmemory':'128mb'}));break
 time.sleep(.1)
else:process.terminate();raise SystemExit('商品缓存 Redis 未能就绪')
