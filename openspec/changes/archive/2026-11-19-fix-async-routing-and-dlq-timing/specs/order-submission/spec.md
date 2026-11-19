## MODIFIED Requirements

### Requirement: ORDER-003 可靠异步受理

系统 SHALL 在返回 PROCESSING 前持久化购买快照、预占意图及可靠受理结果，并确保后续建单命令具有可恢复的发送依据。

#### Scenario: 正常异步受理

- **GIVEN** 共享库存预占成功
- **WHEN** 提交异步购买
- **THEN** 返回 PROCESSING 和原 trace，不冒充已有成功订单。

#### Scenario: 受理提交后进程退出

- **GIVEN** 受理结果已经持久化
- **WHEN** 尚未发送建单消息时进程中断
- **THEN** 恢复后仍能发送同一购买的建单命令。

#### Scenario: 异步建单有效期

- **GIVEN** 应用未覆盖异步建单有效期
- **WHEN** 购买可靠受理
- **THEN** 系统 SHALL 保存受理时起约 600 秒的独立建单期限。重试不修改该期限，真实建单才开始计算支付有效期。
