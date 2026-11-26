#!/usr/bin/env python3
"""从临时 MySQL 库导出分模块 DDL，再导入另一个临时库校验。"""
import argparse
import difflib
import json
import re
import subprocess
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "docs/database/mysql"

# 按外键依赖排序，导入验证时保留外键检查。
MODULES = (
    ("01-users.sql", "用户与地址", ("app_user", "user_address")),
    ("02-catalog.sql", "商品、SKU 与属性", ("product", "product_attribute", "product_sku", "sku_attribute")),
    ("03-cart-coupons.sql", "购物车与优惠券", ("cart_item", "coupon", "user_coupon")),
    ("04-orders.sql", "下单、订单与库存", (
        "order_submit_trace", "customer_order", "order_item", "order_inventory_return")),
    ("05-order-messaging.sql", "订单消息", ("order_message_outbox", "order_event_receipt")),
    ("06-fulfillment.sql", "物流与售后", ("logistics_event", "after_sales")),
    ("07-agent.sql", "Agent 会话、技能与监控", (
        "conversation", "chat_message", "agent_scope_state", "user_memory", "agent_skill",
        "conversation_skill", "pending_action", "agent_model_call", "agent_turn_item")),
    ("08-knowledge.sql", "RAG 源文档与兼容索引", (
        "knowledge_document", "knowledge_index_version", "knowledge_index_head", "knowledge_index_chunk")),
)


def table_ddl(sql, database, table):
    create = sql(f"SHOW CREATE TABLE `{table}`;", database).split("\t", 1)[1].strip()
    # 库内已有种子行时，SHOW CREATE 会显示下一条自增编号；结构快照不带数据水位。
    return re.sub(r" AUTO_INCREMENT=\d+", "", create) + ";"


def normalize(ddl):
    return ddl.replace(" CHARACTER SET utf8mb4 COLLATE", " COLLATE")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mysql", default="/usr/local/mysql/bin/mysql")
    parser.add_argument("--defaults-file", type=Path, default=ROOT / ".runtime/mysql/root.cnf",
                        help="本机私有 MySQL 客户端配置")
    args = parser.parse_args()
    command = [
        args.mysql, "--defaults-extra-file=" + str(args.defaults_file.resolve()),
        "--batch", "--raw", "--skip-column-names", "--default-character-set=utf8mb4",
    ]

    def sql(query, database=None):
        options = ["--database=" + database] if database else []
        return subprocess.check_output(command + options, input=query, text=True, encoding="utf-8")

    token = uuid.uuid4().hex[:12]
    source, verify = f"bitmall_schema_{token}", f"bitmall_schema_check_{token}"
    created = []
    try:
        for database in (source, verify):
            sql(f"CREATE DATABASE `{database}` CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;")
            created.append(database)
        migrations = ROOT / "src/main/resources/db/mysql"
        baseline = max(migrations.glob("B*__*.sql"),
                       key=lambda path: int(path.name.split("__", 1)[0][1:]))
        baseline_version = int(baseline.name.split("__", 1)[0][1:])
        later = sorted(
            (path for path in migrations.glob("V*__*.sql")
             if int(path.name.split("__", 1)[0][1:]) > baseline_version),
            key=lambda path: int(path.name.split("__", 1)[0][1:]),
        )
        # 原生客户端导出使用临时历史标记，种子脚本因此沿用与新库一致的判断。
        history_table = "schema_export_history"
        history = (
            f"CREATE TEMPORARY TABLE `{history_table}` (version VARCHAR(50), type VARCHAR(30));\n"
            f"INSERT INTO `{history_table}` VALUES ('{baseline_version}', 'SQL_BASELINE');\n"
        )
        commands = "\n".join(path.read_text(encoding="utf-8") for path in [baseline, *later])
        sql(history + commands.replace("${flyway:table}", history_table), source)

        tables = set(sql("SHOW TABLES;", source).splitlines())
        ordered_tables = [table for _, _, module in MODULES for table in module]
        if len(ordered_tables) != len(set(ordered_tables)) or set(ordered_tables) != tables:
            raise ValueError(f"模块表名单与迁移结果不一致：缺少 {tables - set(ordered_tables)}，"
                             f"多出 {set(ordered_tables) - tables}")

        ddl = {table: table_ddl(sql, source, table) for table in ordered_tables}
        latest = ([baseline, *later])[-1].name.split("__", 1)[0]
        snapshots = {}
        for filename, title, module in MODULES:
            header = (f"-- {title}｜当前 MySQL 结构（{latest}）\n"
                      "-- 由 scripts/export-database-schema.py 导出。按文件编号顺序导入空库可校验结构。\n"
                      "-- 应用初始化由 Flyway 执行，快照仅含结构、不含业务数据或 Flyway 历史。\n"
                      "SET NAMES utf8mb4;\n\n")
            snapshots[filename] = header + "\n\n".join(ddl[table] for table in module) + "\n"
            sql(snapshots[filename], verify)

        if set(sql("SHOW TABLES;", verify).splitlines()) != tables:
            raise AssertionError("导入快照后表名单不一致")
        for table in ordered_tables:
            expected = normalize(ddl[table])
            actual = normalize(table_ddl(sql, verify, table))
            if actual != expected:
                diff = difflib.unified_diff(expected.splitlines(True), actual.splitlines(True))
                raise AssertionError(table + "\n" + "".join(diff))

        OUTPUT.mkdir(parents=True, exist_ok=True)
        for filename, snapshot in snapshots.items():
            (OUTPUT / filename).write_text(snapshot, encoding="utf-8")
        print(json.dumps({"tables": len(tables), "modules": len(snapshots), "verifiedImport": True,
                          "output": str(OUTPUT.relative_to(ROOT))}))
    finally:
        for database in reversed(created):
            sql(f"DROP DATABASE `{database}`;")


if __name__ == "__main__":
    main()
