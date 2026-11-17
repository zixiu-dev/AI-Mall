## Why

商品详情目前在共享缓存未命中后检查 Bloom。用户希望本地未命中后更早过滤不存在的商品 ID，减少这类请求读取共享详情缓存，并明确暂不建设商家新增商品入口。

## What Changes

- 详情顺序改为参数校验 → Caffeine → Bloom → Redis → 重建锁/双检 → 数据库。
- 本地命中直接返回，Bloom 未就绪/故障仍放行；搜索请求不使用商品 ID Bloom。
- 同步规格、代码、回归、流程图与文档。
- 用户允许 Lombok，更新开发约定；本次不为未使用的注解增加依赖或批量转换类。Java 17 与不使用 record 的约定保留。

## Capabilities

### Modified Capabilities

- `catalog-cache`: CACHE-002 调整存在性检查顺序，保留空值、故障放行和本地快路径。

## Impact

商品缓存组件和相关说明，无数据库迁移；不新增商品、SKU、商家端或发布业务。
