# 项目协作约定

## 功能变更

- 先阅读 openspec/README.md、openspec/project.md 和相关能力规格；架构及实现讲解在 docs/。
- 新增/修改/删除可观察行为时，建立 openspec/changes/<change-name>/，记录 proposal、delta specs、必要 design、tasks 与实际验证；按 openspec/workflow.md 推进。
- 修复违反原契约的缺陷时保留原要求；行为未变的重构/说明可使用 skip_specs，不伪造业务差异。
- 已有明确用户授权时，在同一次任务中推进规格、代码和验证，无需逐文件再次确认；歧义或超出授权范围再具体澄清。
- 完成后同步基线与必要 docs、更新验证映射，再归档。未完成验证或实现的功能保留活动 change 并说明缺口。

## 实现与验证

- Java 17 普通类，不使用 record；允许按需使用 Lombok。类、关键方法和复杂步骤写清中文职责说明。
- 按业务模块保留 Controller / Service / DAO 职责，页面和 Agent 复用同一业务服务。
- 修改交易逻辑时核对幂等、事务、状态、库存和失败恢复，不能把接口受理或 MQ ACK 当成功建单。
- 修改数据库使用新增 MySQL/H2 Flyway 迁移，不改写已应用历史；隔离环境验证真实组件。
- 测试范围与改动相称；只报告实际运行结果，保留错误/积压及当前边界。密钥与私有连接信息不写入文档。
