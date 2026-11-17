# 验证记录（2026-11-17）

实现依据为用户提供的交接文档。凭据仅在本机私有环境；所有交易回归使用隔离组件，未对共享 MQ 发压。

## 实际运行结果

- 最终完整回归：229 项，226 通过、0 失败、0 错误；3 项真实模型测试默认按开关跳过。包含真实临时 MySQL 库、H2 与临时 Redis，MySQL 测试库运行后清理。原文 `.runtime/rag/regression-final.log`，汇总 `.runtime/rag/regression-final.json`。
- 另外显式运行 `RealRagIntegrationTest`：3 项通过、0 跳过，使用真实百炼和现有 DeepSeek 网关。原文 `.runtime/rag/real-tests.log`，汇总 `real-summary.json`。不能把默认跳过写成默认已联网，也不是把两次用例数相加成 232 个唯一测试。
- JAR 打包通过；打包后的独立索引命令在本轮持久 H2 文件库调用真实 embedding 构建 22 块并退出；新本机实例 GET 与 POST 均返回真实 DB_KEYWORD+VECTOR，POST 保留 Apple/RETURNS 与 Nike/SHIPPING 两组。原文 `preview-index.log / preview.log`，结果 `http-report.json / preview.json`。
- 默认 4000 字符证据预算测试检查完整块、最终 JSON 长度、分组与去重并集一致；极端元数据过大明确要求缩小范围，不半截输出政策。
- 文档 SVG 已用 Chrome 渲染 PNG，并人工查看 RAG 图；本地链接与 CLI strict 检查另执行记录。

## 规格映射

| 要求 | 实际检查与边界 |
|---|---|
| RAG-001 | KnowledgeRequestTest：嵌套类型、空白、长度、互斥、先去重再限量；HTTP POST 及脚本 Agent 实际执行校验 |
| RAG-002 | KnowledgePipelineIntegrationTest：原品牌/渠道/地区不丢失，明确配对不交叉，未知品牌和漏查有 gap，多轮唯一主体/歧义，旧 HTTP 显式筛选仍兼容；有限规则，不声称任意自然语言等价证明 |
| RAG-003 | H2 + KnowledgeMySQLIndexIntegrationTest：V19、JSON、锁/原子发布、失败保留旧指针、网络期间原文变化不可发布；停用/源变化/同维异模型/损坏向量失效 |
| RAG-004 | 同版本 SQL LIKE 与向量合法子集扫描，RRF rank=1 得 2/61、保留整型关键词分；真实百炼语义近邻与真实混合证据。未接 BM25/ANN |
| RAG-005 | 原查询与新增问题的请求缓存、品牌/地区/阿拉伯及中文期限/否定改写回退；增强超时回退；真实增强适配器返回严格 JSON |
| RAG-006 | 向量故障保关键词、关键词故障保向量、两路错误、部分组错误、200ms 测试期限取消后台模型工作；HTTP 503 与工具 ERROR，不伪装 NO_MATCH |
| RAG-007 | 分组覆盖、总量/字符预算、保留整块与来源、顶层 hits 为最终并集；coverage 是资料覆盖，不是置信度 |
| RAG-008 / SUPPORT-003 | 脚本与真实 ReAct 先收政策再调用本人订单；既有确认/Skills/会话门禁回归继续通过，不给 RAG 交易工具权限 |

## 真实模型证据

- embedding：`text-embedding-v4`，1024 维；本地兼容标签 `text-embedding-v4-config-1`。该标签不是供应商固定修订承诺。
- 测试发布索引：`knowledge-c7de0c4d-ea17-42cc-b20f-dada5b079c25`；22 篇摘要对应 22 块。
- 非字面近义样本与相关段落余弦 `0.739406`，与配送干扰段落 `0.380286`；真实混合检索实际返回 VECTOR 通路与版本信息。该单例不是 Recall/nDCG 或置信度评估。
- 语义检查 + 单次混合检索测试约 425ms；不是单次 Redis、HTTP 或所有用户请求延迟。
- 真实增强适配器在 8 秒测试上限内耗时 1605ms，返回两条表达。服务默认只给增强 1.5 秒，因此慢请求会按设计回退；不声称该次改写已用于默认关闭增强的 Agent 测试。
- 主模型：`deepseek-ai/DeepSeek-V4-Flash`（现有 SiliconFlow 网关）；实际工具序列：`search_knowledge → search_knowledge → order_query`，约 16858ms。回答分别引用 Apple/Nike 来源及本人订单，无交易确认卡。SDK 持久状态未提供统一 token usage，未编造 token 数或完整计费统计。
- 当前预览 `http://127.0.0.1:18082`，独立文件库，HYBRID + 真实主模型，未启用查询增强；预览发布版本 `knowledge-f82cb758-a290-4e71-acee-28aa641795f6`。进程信息仅保留本机 `.runtime`。

## 保留边界

当前是小库精确扫描，默认最多 1000 块；全量重建、未变文本复用与旧版本自动清理未做。cross-encoder 未接入。仅五个品牌与本站规则；细粒度门店过滤、法律完整性和任意问题自动语义验证不在本轮承诺内。

普通业务与本次知识检索正确性已复验，没有重跑交易压测或扩大为生产容量结论。真实模型仅代表这些固定少量样本。已完成文档与归档前 strict 校验；CLI 已将差异同步基线并归档。第 8 项包含归档本身，在 CLI 完成后才勾选，未把归档前的任务提示当作验证失败。

归档后最终校验：17 个基线规格、6 个归档 change 均通过官方 CLI strict；活动 change 数为 0。本地 Markdown 相对文件链接缺失为 0。已确认提供的 Key 未进入 src/docs/openspec/scripts，私有配置权限为 0600；预览首页返回 HTTP 200。
