package com.bitmall.agent.agent.service.runtime;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 单进程的会话并发门禁。 同一会话同时只允许一次聊天或确认，防止两轮互相覆盖状态；返回的 Lease 在结束或取消时释放执行权。 */
@Component
public class AgentRunGate {
    /** 当前进程正在处理的会话编号集合，登记失败说明同会话已有另一请求运行。 */
    private final Set<String> running = ConcurrentHashMap.newKeySet();

    /** 尝试登记会话正在处理；已登记则拒绝并发，成功后返回释放句柄。 */
    public Lease acquire(String conversationId) {
        if (!running.add(conversationId)) throw new IllegalStateException("当前会话正在处理中，请稍后再试");
        return () -> running.remove(conversationId);
    }

    /** 一次会话执行权的释放句柄。 实现 AutoCloseable，使普通请求可以用 try-with-resources，流式请求可以在结束或取消时自动释放。 */
    public interface Lease extends AutoCloseable {
        /** 释放本轮占用的会话执行权，允许后续聊天或确认继续。 */
        @Override
        void close();
    }
}
