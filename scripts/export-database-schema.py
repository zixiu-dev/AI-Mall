#!/usr/bin/env python3
"""从全新临时 MySQL 库导出当前 DDL，并在第二个临时库验证；不读取已有业务数据，不修改 Flyway 历史。"""
from pathlib import Path
import argparse, json, os, re, subprocess, uuid
ROOT = Path(__file__).resolve().parents[1]

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--mysql', default='/usr/local/mysql/bin/mysql')
    parser.add_argument('--defaults-file', type=Path, default=ROOT/'.runtime/mysql/root.cnf', help='本机私有 mysql client 配置，不把密码写到命令行')
    args = parser.parse_args()
    command = [args.mysql, '--defaults-extra-file='+str(args.defaults_file.resolve()), '--batch', '--raw', '--skip-column-names', '--default-character-set=utf8mb4']
    token = uuid.uuid4().hex[:12]
    source, verify = 'bitmall_schema_'+token, 'bitmall_schema_check_'+token
    created = []
    def sql(query, database=None):
        return subprocess.check_output(command+(['--database='+database] if database else []), input=query, text=True)
    try:
        for name in [source, verify]:
            sql('CREATE DATABASE `'+name+'` CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;'); created.append(name)
        migrations = sorted((ROOT/'src/main/resources/db/mysql').glob('V*__*.sql'), key=lambda p:int(p.name.split('__')[0][1:]))
        # 仅在空库重放仓库 SQL，用于展示最终形态；实际应用初始化仍由 Flyway 管理历史及回调。
        sql('\n'.join(p.read_text() for p in migrations), source)
        tables = sql('SHOW TABLES;', source).splitlines()
        statements = []
        for table in tables:
            assert re.fullmatch(r'[a-z_]+',table), table
            ddl=sql('SHOW CREATE TABLE `'+table+'`;', source).split('\t',1)[1].strip()
            ddl=re.sub(r' AUTO_INCREMENT=\d+', '', ddl)
            statements.append('-- '+table+'\n'+ddl+';')
        header='''-- 当前 MySQL 表结构快照；由 scripts/export-database-schema.py 从空库重放 V1～V20 后导出。
-- 阅读/对照用途，含表与索引，不含业务数据、Flyway 历史、密钥或旧演化语句。
-- 正常初始化请启动应用交给 Flyway；不要先导入本文件再让 Flyway 执行 V1。
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS=0;

'''
        snapshot=header+'\n\n'.join(statements)+'\n\nSET FOREIGN_KEY_CHECKS=1;\n'
        sql(snapshot,verify)
        assert sql('SHOW TABLES;',verify).splitlines()==tables
        for table,statement in zip(tables,statements):
            actual=sql('SHOW CREATE TABLE `'+table+'`;',verify).split('\t',1)[1].strip()
            expected=statement.split('\n',1)[1]
            normalized=re.sub(r' AUTO_INCREMENT=\d+','',actual)+';'
            if normalized.replace(' CHARACTER SET utf8mb4 COLLATE', ' COLLATE')!=expected.replace(' CHARACTER SET utf8mb4 COLLATE', ' COLLATE'):
                import difflib
                raise AssertionError(table+'\n'+''.join(difflib.unified_diff(expected.splitlines(True),normalized.splitlines(True))))
        output=ROOT/'docs/database/mysql-current-schema.sql';output.write_text(snapshot)
        print(json.dumps({'tables':len(tables),'verifiedImport':True,'output':str(output.relative_to(ROOT))}))
    finally:
        for name in reversed(created):sql('DROP DATABASE `'+name+'`;')
if __name__=='__main__':main()
