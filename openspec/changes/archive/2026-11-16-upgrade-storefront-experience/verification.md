# 商城前台验证记录

## 本轮实际执行

- `mvn -q -DskipTests package` 成功；仅构建，不把跳过的 Java 测试写成通过。
- `node --check` 检查 app.js、storefront.js、navigation.js 和 verify-storefront.cjs 成功。
- 最终打包 JAR、专用 demo/H2、真实 Chrome/HTTP，`verify-storefront.cjs` 完成 16 项检查；报告时间 2026-11-16T15:35:35.621Z。
- 正常页面无未预期的 JavaScript 或资源错误。刻意丢弃一次已提交响应、模拟一次商品 404，预期故障单独记录。

## 要求映射

| 要求 | 验证及实际结果 |
|---|---|
| FRONT-001 | 真实目录、关键词预算和 SKU，商品直达、刷新与历史返回通过 |
| FRONT-002 | 真实购物车勾选、全选、金额；取消移除不删除；真实领券及筛选通过 |
| FRONT-003 | 真实试算及建单、待付款、倒计时、订单详情与物流；取消/模拟支付后核对 API；丢弃真实提交响应后只发一次 POST 并以原 trace 恢复通过 |
| FRONT-004 | 桌面与 360/390/768 宽度七个页面无横向溢出；检查有商品的手机购物车/结算截图；规则助手真实 SSE 与模式标识；无效商品失败反馈通过 |

证据：[报告](../../../../.runtime/storefront-tests/report.json)、[验收脚本](../../../../scripts/verify-storefront.cjs)、[测试总文档](../../../../docs/testing.md#12-商城前台浏览器验收)、[前台设计和截图](../../../../docs/storefront-design.md)。

## 验证边界

H2、SYNC、无 Redis/MQ、规则助手和模拟支付。不声称重新执行历史 174 项后端测试，也不声称完成真实异步 MQ、真实模型、付款渠道、Safari/Firefox、跨时区部署或性能压测。手机使用 Chrome 设置视口验证，未声称真实手机硬件验收。

## 规格检查

活动 change 与归档前全部 15 个基线严格验证通过。归档由官方 CLI 合入 4 条前台要求，基线增至 16 个能力；归档后全量、归档及文档链接检查见最终校验记录。

最终校验：16 个能力基线严格验证全部通过，3 个归档严格验证全部通过；README、docs 与 openspec 的本地 Markdown 文件链接零失效。原始 CLI 结果保存在本机 `.runtime/storefront-tests/openspec-validation.json` 和 `openspec-archive-validation.json`。
