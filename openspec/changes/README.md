# 变更工作区

目前没有待实施的功能变更。新增功能使用 `new change <name>`，按 [工作流](../workflow.md) 建立独立目录。

- 活动 change 保存这次的 proposal、delta specs、design、tasks 和实际 verification。
- 基线 specs 代表已生效行为，不把尚未实现的 delta 提前写成现状。
- 完成后 archive 同步基线并移入 archive。

已完成的 [15 分钟关单案例](archive/2026-11-16-add-payment-timeout/proposal.md)是根据本次已有代码与证据追溯整理，已与基线一致，不要重放。

已完成的 [Redisson 统一与缓存顺序变更](archive/2026-11-16-unify-redisson-cache-flow/proposal.md)包含规格差异、实施任务及本次真实验证。当前基线已经同步。
