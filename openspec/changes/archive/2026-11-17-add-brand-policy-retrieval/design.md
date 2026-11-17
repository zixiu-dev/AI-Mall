# 品牌政策检索设计

保留原生关键词检索，先识别品牌与类别再在过滤集合内召回。ReAct 模型通过工具参数表达语义判断，KnowledgeIntentService 以别名和类别规则补全、规范化并校验矛盾；不新增一次外部 LLM 调用。

KnowledgeSearchResult 返回意图、状态、提示与 hits。含冲突或未知信息时明确 CLARIFICATION_REQUIRED；未收录返回 NO_MATCH；正常 FOUND。无品牌的普通商城规则归 PLATFORM；仅保修或笼统售后需补问品牌/具体问题。多品牌/多类别分组检索，不能用一个品牌的高分吞掉另一个。

知识文档按品牌/类别保存在 resources/knowledge，manifest 记录 metadata；生成不可覆盖的 V18 迁移。平台规则保留但改为忠实描述当前演示服务，删除跨品牌统一保修等误导。品牌文档注明中国大陆、官方渠道与核对日期，保修不能跨产品推断年限。

SQL 使用绑定参数、转义 LIKE 特殊字符；最多处理有限分组与命中，限制输入长度。旧 search(String) 适配保留 List 返回，新工具与 HTTP 使用完整结果。来源 URL 只接受 HTTPS，前端只解析安全 HTTPS Markdown 引用。

通过 Service/Mapper 与真实 MySQL/H2 验证隔离召回；AgentScope 测试模型验证结构化工具入参和可见来源，真实远端模型质量单列边界。浏览器检查引用链接、补问、无结果与政策咨询不会创建确认卡。
