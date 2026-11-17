# 运行指标与验证口径

## Purpose

约定运行时可查看的交易与缓存状态，以及查询、异步受理、成功建单和后置恢复的计量边界，保证压测结果具有场景、环境和原始证据，避免把接口快速响应或短时峰值当成完整业务链路的持续容量。

## Requirements

### Requirement: OBS-001 运行状态可观察

系统 SHALL 通过管理端提供缓存、下单、路由及基础健康/指标信息，并在独立配置的管理地址上暴露。

#### Scenario: 查看缓存与下单

- **GIVEN** 应用管理端已启用对应端点
- **WHEN** 读取 catalogcache / ordermonitor
- **THEN** 能够区分命中、回源、在途、积压及路由状态；未知不能伪装为零压力。

### Requirement: OBS-002 一秒吞吐按真实事件计数

系统 SHALL 在显式启用逐秒统计后使用完整固定 1000ms 桶，分别记录 HTTP 总响应/2xx、可靠受理和事务提交后的新订单。

#### Scenario: 异步返回处理中

- **GIVEN** 购买只是完成可靠受理
- **WHEN** HTTP 返回 PROCESSING
- **THEN** 可计成功受理/响应，但不计成功建单 TPS。

#### Scenario: 回滚与重复消费

- **GIVEN** 事务回滚或消息仅返回既有订单
- **WHEN** 进行逐秒统计
- **THEN** 不得新增一次成功建单计数。

#### Scenario: 未开启秒桶

- **WHEN** 应用使用默认配置运行
- **THEN** 不宣称 throughput 端点已经装配并暴露。

### Requirement: OBS-003 性能结论与积压边界

系统 SHALL 在报告中同时说明环境、工作集、连接/到达模式、延迟、错误率、采样区间和恢复积压，不把不同口径或版本的结果直接混用。

#### Scenario: 只测到一秒峰值

- **WHEN** 编写容量或面试指标
- **THEN** 明确是一秒观测结果，不宣称已证明生产持续容量。

#### Scenario: 订单成功但后置积压

- **GIVEN** 成功建单之后仍有确认或发送任务
- **WHEN** 汇报本轮结果
- **THEN** 分别记录后置积压，不能把建单成功说成整条链路已追平。

## Current limitations

现有告警只是示例，尚无完整生产监控平台。历史压测是各自代码版本的本机结果；新增每单定时消息后没有重新测出新的峰值容量。

## References

[设计与流程](../../../docs/testing.md) · [验证入口](../../../src/test/java/com/bitmall/agent/common/monitor/OneSecondThroughputTest.java)
