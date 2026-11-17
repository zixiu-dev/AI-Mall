# 验证记录

日期：2026-11-17。实现与实际测试已完成；本次为 SQL 元数据过滤/关键词检索，不引入向量库、远端意图模型或交易写权限。

| 要求/变化 | 方法与实际结果 |
|---|---|
| SUPPORT-003 品牌类别检索、来源元数据 | BrandPolicyIntegrationTest 与真实 MySQL 用例核对过滤、来源/日期、LIKE 转义；22 篇清单和生成的双数据库 V18 一致 |
| SUPPORT-004 别名、对比、冲突和缺失 | iPhone/米家等别名、两品牌分别返回、参数矛盾补问、未知品牌与 Nike 保修无资料均通过；明确不跨品牌回退 |
| SUPPORT-005 渠道、引用和只读边界 | 本站 iPhone 查 PLATFORM；咨询不创建确认卡或售后记录；实际 Chrome 验证来源、安全链接、HTML 转义与手机布局 |
| 数据兼容性 | 独立 H2 V17→V18，旧订单、SKU/库存和原知识文本保持；旧知识仅停用；真实 MySQL V18 通过 |
| AgentScope 集成 | 脚本模型调用真实工具核对结构化意图及来源；超预算按完整文档移除并提示 PARTIAL，无额外 LLM 请求 |

全量 **200 项通过，零失败/错误/跳过**，包含隔离 MySQL 与真实临时 Redis。浏览器 **8 组检查通过**（含 10 组查询 API）、未预期错误 0；HTTP/SSE 为专用 demo 真实服务，安全渲染单项使用 SSE 夹具。没有真实远端模型效果测试、MQ 联调或容量测试。

证据与复现：[测试总文档第 14 节](../../../../docs/testing.md#14-品牌政策知识检索验证2026-11-17)、[回归原文](../../../../.runtime/brand-policies/regression.log)、[浏览器报告](../../../../.runtime/brand-policies/browser-report.json)、[知识设计](../../../../docs/knowledge-design.md)。.runtime 为本机证据，不随仓库提交。

已核对构建 JAR 含 22 篇原文、manifest 与双数据库 V18，测试夹具不进入应用包；本地链接、迁移生成一致性和活动 change strict 校验通过。归档已将 SUPPORT-003/004/005 同步到基线；归档后 strict 校验 16 个基线、5 个历史变更全部通过，本地 Markdown 链接检查通过。
