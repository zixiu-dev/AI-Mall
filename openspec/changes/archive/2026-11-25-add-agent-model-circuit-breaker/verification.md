# 验证记录

本机 Java 17 与 Maven。使用本机 HTTP 模型替身和真实 Spring AI 传输层，后台回答集成使用本机 HTTP 与隔离 H2。下列 Maven 组不使用真实百炼凭据。

```bash
mvn -o -q -Dtest=ModelCircuitBreakerTest,ModelGatewayTransportTest,SpringAiStreamReaderTest,SpringAiIntegrationTest,AgentResumeHttpIntegrationTest,OpenAITransportTest,AgentFlowIntegrationTest,ToolCallRecoveryTest,AgentActionRecoveryTest,LayeredArchitectureTest,PlainJavaModelsTest test
npx --yes @fission-ai/openspec@2.0.0 validate --all --strict --no-interactive
git diff --check
```

- Maven 72 项通过，0 失败、0 跳过。可控时钟和并发调用覆盖两次连续失败、成功清零、过期旧许可、单请求半开探测、探测失败重新冷却和取消释放。
- 本机 OpenAI 兼容端点模拟截断响应、HTTP 503 与 HTTP 400。覆盖草稿重置、百炼候选的 URL 和模型名、携带原工具回执及工具定义、熔断期间跳过、错误分类、输入 Token 与缺失用量。
- HTTP 后台流验证领券提议后模型失败，直接展示原确认卡。重连返回同一个确认卡编号。
- 启用备用时，Spring 默认仍向其他组件注入主模型客户端，备用通过限定名注入模型网关。
- OpenSpec 严格校验 20 项通过。Git 差异检查通过。

## 百炼真实联调

先使用阿里云百炼北京 OpenAI 兼容公共端点与 `qwen-plus` 发起一次短请求，返回 HTTP 200，并包含响应和用量。通过进程环境向 Maven 子进程提供凭据，版本库和验证记录不保存凭据。

运行 `mvn -o -q -Dtest=RealBailianGatewayIntegrationTest test`，通过 1 项。该用例按 `AGENT_BAILIAN_LIVE_TEST=true` 启用，读取 `AGENT_BAILIAN_API_KEY` 和 `AGENT_BAILIAN_BASE_URL`。

- 使用真实网关和 Spring AI 客户端。主模型本地注入连接失败，百炼接收三次合成推理，分别返回文本流、合成 SKU 工具请求和基于工具结果的回答。
- 前两次各尝试主模型一次并触发熔断，第三次跳过主模型。工具未被 SDK 自动执行，人工构造的库存 17 件回执能继续生成回答。
- 三次远端流均报告了用量，累计输入 408、输出 55 Token。
- 半开并发、冷却和迟到许可用本地测试覆盖。联调不调用真实商城交易，不需要在生产环境制造故障。
