# 统一 Redisson 并修正缓存重建顺序

## Why

当前接口锁、缓存重建锁和布隆存在多套实现，商品不存在时仍会先争用本机锁。用户要求除 MQ 消费去重外统一使用 Redisson，并让存在性过滤发生在缓存构建锁之前，为多实例部署保留可解释的并发保证。

## What Changes

- 商品缓存命中直接返回；未命中后先 Bloom，再竞争 Redisson RLock，锁后双检再受控回源。
- 用原生 RBloomFilter 保存商品存在性，保留未就绪/数据丢失放行、未提交登记保留和构建发布保护。
- 商品读写、失效订阅、库存原子脚本、压力协调通过 Redisson 对象或 RScript 访问 Redis。
- MQ 的消息发送/消费与消费去重行为保持原有规则。交易 Redis 键和库存状态不迁移、不清空。

## Capabilities

### New Capabilities

无新增业务能力。

### Modified Capabilities

- `catalog-cache`：CACHE-002 存在性过滤先于重建锁；CACHE-003 多实例锁及所有者保护；保留其余缓存语义。

## Impact

涉及缓存/Redisson 装配、Bloom、缓存重建、库存 Redis 和路由适配、测试、说明和配图。无表结构变化。Bloom 和缓存锁使用新版本键，旧位图不能直接作为 RBloomFilter 数据解释；切换部署需停止旧缓存构建者。MQ 去重保留隔离的旧客户端。
