# 实际验证

2026-11-17 执行 `mvn -q -Dtest=CatalogCacheReadOrderTest,CatalogCacheIntegrationTest,PlainJavaModelsTest test`：31 项通过，0 失败/错误/跳过；其中新顺序单测3、真实本机隔离 Redis 集成23、模型兼容5。日志 `.runtime/cache-read-order/tests.log`，汇总 `summary.json`。不是全套回归或峰值测试。

CACHE-002：明确否定不读共享缓存/不申请锁/不回源；可能存在先 Bloom 后共享缓存；本地命中不访问 Bloom、过滤器丢键放行、误判后的空值兜底继续通过。搜索不使用商品 ID Bloom。参数非法先被 ProductService 拒绝，旧集成用例保留。

初跑失败为旧测试固定100个ID至多一次误判的概率假设（实际2次），修正为预选100个明确否定ID，断言零回源与零锁竞争，误判另测。未放宽业务规格。

商品新增、SKU 发布、商家端均未实施；文档已说明 registerBeforePublish 只有预留方法/测试，存量通过扫描维护。Lombok 使用许可写入 AGENTS/project/config，不引入未使用依赖或转换现有类。

流程图 catalog-cache-flow 的 SVG/PNG 已重绘，README/docs 相对文件链接检查通过。无数据库变更，不改历史迁移。未重启现有开发预览（该预览关闭商品缓存）。
