package com.bitmall.agent.common.idempotent;

/** 相同请求仍在执行。HTTP 返回冲突提示，MQ 抛异常后由 Broker 稍后重新投递。 */
public class IdempotentBusyException extends RuntimeException {
    /** 创建可供页面或消费日志阅读的重复执行提示。 */
    public IdempotentBusyException() {
        super("该请求正在处理，请使用原 traceId 查询或稍后重试");
    }
}
