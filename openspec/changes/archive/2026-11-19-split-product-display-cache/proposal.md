## Why
一笔订单原本会让整个目录的缓存同时失效，其他商品明明没有变化却需要重新查询数据库；异步预扣已经完成但建单尚未提交时，详情直接读取数据库库存还会高估可接单量。

## What Changes
按商品缓存资料，按 SKU 批读价格和库存，建单和关单只更新对应库存的展示。

## Capabilities
### Modified Capabilities
- catalog-cache：拆分资料、价格与库存。

## Impact
保留前台 API，交易库无需迁移。
