# 新增与修改功能：工作顺序

返回 [OpenSpec 导航](README.md)。以下命令在仓库根目录运行，使用固定 CLI 2.0.0。你描述需求后，开发者/Agent 按这些步骤直接推进。

## 1. 确定改哪个能力

先读 project.md、相关 specs 和 docs，再确认问题。以已存在的能力目录为准，避免同一功能又创建一份近义规格。

| 需求类型 | 如何记录 |
|---|---|
| 新行为或新接口 | 新建 change；已有能力里 ADDED，新能力则新目录加 Purpose |
| 修改金额、期限、状态、兼容行为 | change 中 MODIFIED；复制原要求及全部场景后修改 |
| 删除或改名 | REMOVED 写原因和迁移，RENAMED 写完整旧/新标题 |
| 修复违反现有规格的缺陷 | proposal 引用原编号，设计与任务说明修复；契约没变就不假造规格差异 |
| 纯重构、工具或说明文档整理 | 简短记录范围与验证；不改变行为时可 skip_specs |
| 只有基线 Purpose 文字修正 | 直接修正 Purpose，不改业务含义；实质契约改变仍走 change |

小修复无需长篇设计；跨模块、交易、持久化或故障语义改动应写清 design。已有用户授权是推进工作的依据，不把文档写作变成反复申请许可。

## 2. 建立一个独立 change

使用动词开头的 kebab-case 名称，例如 `update-payment-window`；不要把同一能力拆出多个重复基线。

```bash
npx --yes @fission-ai/openspec@2.0.0 list --specs
npx --yes @fission-ai/openspec@2.0.0 list
npx --yes @fission-ai/openspec@2.0.0 new change update-payment-window --schema spec-driven
```

`new change` 只建立变更目录与元数据，不自动替你完成设计。参照 templates 和已完成关单案例，依次填写 proposal、差异 specs、必要的 design、tasks。

proposal 的 Capabilities 必须用已有能力路径的准确名字；实际差异放在 `changes/<change>/specs/<capability>/spec.md`，此时不抢先改已生效基线。

## 3. 写可验收的差异

```markdown
## MODIFIED Requirements

### Requirement: LIFE-001 新订单支付窗口

系统 SHALL 为新订单持久化支付截止时间，默认从真实建单起 600 秒；已有订单沿用原期限。

#### Scenario: 默认支付窗口

- **GIVEN** 应用未覆盖支付有效期
- **WHEN** 同步或异步真正建立订单
- **THEN** 截止时间约为建单时间加 600 秒，不消耗受理排队时间
```

上例仅演示差异语法，**不是待实施需求，也不是完整可提交差异**。实际修改 LIFE-001 必须先复制其完整要求和“重试不延长窗口”等全部场景，再修改必要部分，不能归档时把未改变的约束丢掉。

- ADDED：新要求；新能力还要有明确 Purpose。
- MODIFIED：原标题必须匹配，完整覆盖更新后的要求与场景。
- REMOVED：注明 Reason / Migration，不直接从基线删除后失去历史。
- RENAMED：FROM / TO 写准确原名与新名，内容也变化时同时提供修改内容。

缺陷或重构若确实没有契约变化，在 `.openspec.yaml` 加 `skip_specs: true`，proposal 明确“保留哪些原要求”，verification 证明没有回归。不能用该标志绕过真实行为变化。

## 4. 实现并逐项验证

任务按数据模型/业务/消息或交互等实际范围分组，带要求编号和验证方式。实现时发现方案不可行，先更新 design 与相应 delta，再继续，不能只在对话里改了设计。

测试选择与变化相关：缓存/Lua 需要真实 Redis；数据库迁移和方言需要独立 MySQL；定时发送替身只验证适配层，真实 Broker 联调另记。纯排版不需要运行完整交易压测。

实际完成才将 tasks 复选框打勾。在 verification 中逐条写：需求或场景 → 检查方法 → 实际结果 → 证据/尚未覆盖，不把“计划运行”写成“通过”。

```bash
npx --yes @fission-ai/openspec@2.0.0 validate update-payment-window --type change --strict --no-interactive
npx --yes @fission-ai/openspec@2.0.0 status --change update-payment-window
```

CLI 的 artifact done 表示文件存在等工作流状态，不能替代测试结果。影响接口或运维时同步 docs；最终有实质业务检查未完成，就保留为活动 change 并说明缺口。

## 5. 同步规格并归档

在实现与相关验证完成、tasks 标记真实完成之后：

```bash
npx --yes @fission-ai/openspec@2.0.0 archive update-payment-window --yes
npx --yes @fission-ai/openspec@2.0.0 validate --all --strict --no-interactive
npx --yes @fission-ai/openspec@2.0.0 validate --archived --strict --no-interactive
```

默认 archive 将 delta 合入当前 specs，并把 change 移到 archive；归档之后还要检查基线语义、要求标题、验证映射和 docs 是否相符。不要在活动 change 中先手工同步一次，再不检查就归档重放 ADDED。

若使用独立的 sync 操作提前同步，需先核对基线与 delta 是否已合并，并按官方对应工作流归档；不要盲目重复应用。当前关单案例已完成基线同步且已在 archive 内，不能再次当活动变更执行。

`--yes` 只关闭 CLI 交互提示，不表示用户批准了需求变更、发布、删库或外部操作。用户没有请求发布时，归档本地文档不自动部署或提交 Git。

## 6. 一次变更的完成条件

- 用户范围、受影响能力和兼容规则明确。
- 相关差异与设计反映最终实现，代码保持模块边界及中文说明。
- 影响数据库时有新增迁移并验证正确数据库；失败、重复与恢复场景按实际变化覆盖。
- verification 有真实证据，未完成项明确可见；tasks 不虚假打勾。
- 基线、测试映射、必要 docs 同步，完成 change 再归档。
