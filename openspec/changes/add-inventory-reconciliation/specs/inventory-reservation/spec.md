## ADDED Requirements

### Requirement: STOCK-005 库存对账

系统 SHALL 按批次只读核对库存，结合 MySQL 建单和关单事实以及 Redis 预占状态计算期望可售量，记录可核实差异和需要复核的项目。

#### Scenario: 建单前后正常占用

- **GIVEN** 一笔预占减过 Redis 可售
- **WHEN** 核对尚未建单、MySQL 已建单但 Redis 未确认、库存确认完成的三个阶段
- **THEN** 仅在 MySQL 尚未扣减期间从数据库可售数扣除这笔在途预占。

#### Scenario: 关单后 Redis 待返还

- **GIVEN** MySQL 已关单返还，Redis 返还任务仍在处理
- **WHEN** 对账核对返还任务和 Redis 预占状态
- **THEN** 仅在 Redis 尚未返还时扣除这笔在途返还，Redis 已返还而 SQL 进度尚未更新时不重复扣除。

#### Scenario: 缺失或采样证据不完整

- **GIVEN** 已初始化库存池缺失、预占无法关联、采样期间状态变化或依赖不可用
- **WHEN** 执行对账
- **THEN** 记录缺失或待复核证据，保留现有库存值和预占记录。

#### Scenario: 发现库存差异

- **WHEN** 有足够证据计算且实际量与期望量有差异
- **THEN** 记录有符号差异和依据，按阈值告警，业务库存由既有恢复流程维护。
