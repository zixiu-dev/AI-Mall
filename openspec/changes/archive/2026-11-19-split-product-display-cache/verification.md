2026-11-19：隔离 H2 + 随机回环端口真实 Redis，离线 Maven 打包成功。最终命令：

```sh
mvn -q -o -Dtest=CatalogCacheIntegrationTest,CatalogCacheReadOrderTest,InventoryReservationIntegrationTest,CommerceModulesIntegrationTest,LayeredArchitectureTest,PlainJavaModelsTest,SpringAiIntegrationTest package
```

7 组共 80 项，失败/错误/跳过均为 0。`hundredOrdersKeepBothProfilesWarm` 连续建单 100 次，每次读两款商品；库存 120→20，资料回源计数未增加。`staleOfferBatchCannotRestoreInvalidatedStock` 验证库存失效后，同批只有未被删除的价格能回填。回滚、取消返库、Bloom 空值/并发重建/失锁保护均有覆盖。`detailShowsReservationBeforeDatabaseDeductionWithoutInitializingOnRead` 验证初读不建池，异步已受理但数据库仍有 10 时详情返回 9，丢失已初始化的池返回不可用且不修复。

前端 JavaScript 和多 key 冷缓存脚本语法检查通过；两张文档图重新渲染。未重测峰值吞吐、外部模型或真实 Broker，本次没有修改消息协议。
