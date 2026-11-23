## 会话恢复验证

- `mvn -o -Dtest=AgentFlowIntegrationTest test` 通过 13 项。快照用例覆盖历史消息、待确认卡、跨用户拒绝、未知会话拒绝、确认完成后恢复订单号和过滤内部工具消息。
- `node --input-type=module --check < src/main/resources/static/app.js` 通过。浏览器实测和真实 Broker 不在此次定向验证范围内。
