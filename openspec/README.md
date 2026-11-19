# OpenSpec：功能契约与变更记录

这是本项目的规格驱动开发目录，使用标准名称 `openspec/`。基线按 2026-11-16 的实现整理，包含刚完成的 15 分钟未支付自动关单。

**最先记住三件事：specs 说明当前行为；changes 说明这次准备改什么；验证通过并同步规格后归档。**

## 先从哪里看

| 文件 / 目录 | 回答什么 |
|---|---|
| [project.md](project.md) | 项目背景、技术约定、术语与现有边界 |
| [specs/](specs/) | 当前已实现的能力和可验收场景 |
| [workflow.md](workflow.md) | 新增、修改、修复、删除功能的操作顺序与命令 |
| [templates/](templates/) | 本项目的中文提案、设计、差异、任务和验证模板 |
| [changes/](changes/) | 正在进行的变更；当前没有待实施变更；Spring AI / Boot 4 迁移、Agentic RAG 及 PostgreSQL 向量存储均已完成验证归档 |
| [关单案例](changes/archive/2026-11-16-add-payment-timeout/proposal.md) | 已实现后追溯整理的完整示例 |
| [Redisson 变更](changes/archive/2026-11-16-unify-redisson-cache-flow/proposal.md) | 按规格、实现、验证、同步归档完成的变更 |
| [前台体验变更](changes/archive/2026-11-16-upgrade-storefront-experience/proposal.md) | 商城页面、购买交互及浏览器验收 |
| [真实目录变更](changes/archive/2026-11-17-replace-demo-catalog-with-real-products/proposal.md) | 真实品牌型号、参考价格与图片来源 |
| [品牌知识变更](changes/archive/2026-11-17-add-brand-policy-retrieval/proposal.md) | 品牌/类别识别、官方政策来源与渠道隔离 |
| [Spring AI 迁移](changes/archive/2026-11-17-migrate-to-spring-ai/proposal.md) | Boot 4 / Spring AI 2、工具与状态迁移、当前数据库结构资料 |
| [PostgreSQL 向量存储](changes/archive/2026-11-17-add-pgvector-knowledge-store/proposal.md) | 原生vector/JSONB、独立连接与迁移、数据库精确/HNSW查询 |
| [Agentic RAG 变更](changes/archive/2026-11-17-build-agentic-rag-pipeline/proposal.md) | 多问题契约、离线索引、百炼/DeepSeek 真实验证 |
| [verification-matrix.md](verification-matrix.md) | 哪份规格关联哪些测试和原始证据 |
| [config.yaml](config.yaml) | 官方工作流读取的项目上下文与写作规则 |

[docs/](../docs/README.md)继续负责“怎样实现、面试怎么讲”的图文说明；这里负责“系统必须表现成什么样、这次改了什么”。发现文档与代码不一致时先核对证据，明确是缺陷还是需要变更契约，不能悄悄改规格替失败实现开脱。

## 标准目录

```text
openspec/
├── README.md / project.md / workflow.md
├── config.yaml
├── AGENTS.md                     维护规格的格式约定
├── verification-matrix.md
├── specs/<capability>/spec.md     当前行为基线
├── templates/                    项目补充模板，不冒充官方内置模板
└── changes/
    ├── <change-name>/             后续每项变更独立目录
    │   ├── .openspec.yaml
    │   ├── proposal.md            为什么改、范围与受影响能力
    │   ├── specs/<capability>/spec.md  只描述规格差异
    │   ├── design.md              怎么实现、取舍、事务与故障
    │   ├── tasks.md               可执行任务及完成标记
    │   └── verification.md        本项目补充：本次实际验证证据
    └── archive/<date>-<change>/   已完成历史
```

## 当前 17 个能力

| 能力目录 | 内容 | 要求数 |
|---|---|---:|
| product-catalog | [商品目录与 SKU 查询](specs/product-catalog/spec.md) | 4 |
| catalog-cache | [商品展示缓存保护](specs/catalog-cache/spec.md) | 6 |
| shopping-cart | [用户购物车](specs/shopping-cart/spec.md) | 3 |
| coupons | [优惠券领取与使用](specs/coupons/spec.md) | 3 |
| checkout | [结算试算与成交参数](specs/checkout/spec.md) | 3 |
| order-submission | [购买意图与同步异步提交](specs/order-submission/spec.md) | 5 |
| inventory-reservation | [共享库存预占与幂等整理](specs/inventory-reservation/spec.md) | 4 |
| order-lifecycle | [支付取消与十五分钟超时关闭](specs/order-lifecycle/spec.md) | 4 |
| order-messaging | [可靠消息发送与消费](specs/order-messaging/spec.md) | 4 |
| order-routing | [下单压力路由](specs/order-routing/spec.md) | 3 |
| agent-runtime | [助手会话与受控工具执行](specs/agent-runtime/spec.md) | 5 |
| agent-confirmation | [持久化确认与业务写操作](specs/agent-confirmation/spec.md) | 3 |
| agent-memory | [会话历史、模型上下文与偏好](specs/agent-memory/spec.md) | 4 |
| customer-service | [物流售后与规则查询](specs/customer-service/spec.md) | 5 |
| storefront-experience | [商城前台与购买体验](specs/storefront-experience/spec.md) | 5 |
| knowledge-retrieval | [多查询、混合检索与证据](specs/knowledge-retrieval/spec.md) | 9 |
| observability | [运行指标与验证口径](specs/observability/spec.md) | 3 |

每个要求都有稳定编号，例如 LIFE-003；修改时保留完整 Requirement 标题，新增时使用新编号，不重新编号整篇文档。`SHALL` 表示明确要求；Scenario 中 `GIVEN` 是前提、`WHEN` 是触发、`THEN` 是验收结果，关键字保留英文，说明用中文。

## 今后你可以这样提需求

> 按 OpenSpec，增加 Agent 修改待确认订单数量的能力。先建立 change，说明旧确认卡怎么失效、怎么重新试算，再完成代码和验证。

> 按 OpenSpec，把新订单默认支付期限从 15 分钟改为 10 分钟。明确只影响新订单，重试不能延长截止时间，补充对应场景。

> 按 OpenSpec，修复同 trace 重复创建订单的问题。对照 ORDER-002 定位差异，不放宽已有唯一性要求。

已有明确授权的任务可以在同一轮完成“规格 → 设计 → 代码 → 验证 → 归档”，不要求每个文件单独再批准。需求存在会改变业务语义的歧义时，应把问题具体化后澄清。

## CLI 与维护

本目录使用官方 `spec-driven` schema，并以 **OpenSpec CLI 2.0.0** 验证；Node 要求为 22.12.0 或以上。无需为 Java 项目增加 package.json，也无需全局安装。

```bash
npx --yes @fission-ai/openspec@2.0.0 list --specs
npx --yes @fission-ai/openspec@2.0.0 validate --all --strict --no-interactive
npx --yes @fission-ai/openspec@2.0.0 validate --archived --strict --no-interactive
```

首次执行会获取固定版本；本轮验证使用临时 npm 缓存，没有生成全局 Agent Skills 或额外客户端命令。今后如希望安装官方编辑器/Agent 集成，可另行执行官方 init；当前纯目录已经可以被 CLI 识别和校验。

这些校验检查文档结构与部分一致性，不执行 Java 测试，也不证明所有场景都有自动化覆盖。变更的真实检查记录写 verification.md，合并到 [测试总文档](../docs/testing.md) 的适当位置，避免每次再造一份重复测试说明。

官方依据：[概念与差异规格](https://github.com/Fission-AI/OpenSpec/blob/main/docs/concepts.md)、[默认 schema](https://github.com/Fission-AI/OpenSpec/blob/main/schemas/spec-driven/schema.yaml)、[配置说明](https://github.com/Fission-AI/OpenSpec/blob/main/docs-lab/customize/project-config.md)。这里的 project.md、验证映射和 verification.md 是项目补充，不声称是官方必需文件。
