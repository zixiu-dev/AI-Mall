## Decisions

移动 CatalogCache.get 中详情 Bloom 检查至 readLocal 未命中后、第一次 redis.lookup 前。只对 product:* 的 DetailValue 使用过滤；缓存禁用/事务内仍绕过展示缓存。读 Redis、重建锁、锁后双检、代际保护、空值与 TTL 行为保持原职责。

新增顺序回归验证：过滤明确否定时即便共享缓存存在旧值也不读它、不抢锁、不回源；可能存在先过滤再读共享缓存；搜索不调用 Bloom。已有真实 Redis 回归覆盖本地命中、过滤器丢键/故障、锁与空值。

商家端暂不实施。registerBeforePublish 当前只有测试接入；初始化及周期扫描维护存量商品 ID。文档必须准确陈述此边界，不能把预留方法描述为完整发布链路。
