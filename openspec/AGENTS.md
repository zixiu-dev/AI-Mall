# OpenSpec 文件维护约定

本目录用于当前行为规格与可追溯变更。先读 README.md、project.md、workflow.md 及相关 specs。

- `specs/<capability>/spec.md` 是已生效行为；未完成变化放 changes，不提前冒充现状。
- 使用官方 spec-driven 结构。Requirement / Scenario / SHALL / WHEN / THEN 关键字保留英文，业务说明中文。
- Requirement 标题和编号稳定；每条有可验收场景。描述用户或下游可依赖的行为，实现类、SQL 和线程细节放 design。
- MODIFIED 复制原要求全块再编辑；不丢掉未变场景。新增、删除、改名按各自 delta 区块。
- 不从 docs 中复制大量实现讲解；用链接对应 docs 和测试，明确已实现与已知缺口。
- tasks 只勾实际完成；verification 区分本次新执行、历史证据、替身和真实组件。
- archive 只记录已完成变更；当前关单案例是追溯历史，勿重放或伪装成当时事先产出的文档。
- CLI 格式验证不等于业务测试；修改规格不能用来掩盖与原契约不符的实现。
