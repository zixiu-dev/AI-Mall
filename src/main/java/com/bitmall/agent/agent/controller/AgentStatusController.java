package com.bitmall.agent.agent.controller;

import com.bitmall.agent.agent.config.AgentProperties;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 仅公开执行模式；不泄露模型凭证，也不把配置状态当作远程模型健康检查。 */
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentStatusController {
    /** 读取是否启用真实模型，用于状态接口展示当前运行模式。 */
    private final AgentProperties properties;

    @GetMapping("/status")
    public Map<String, String> status() {
        return Map.of("mode", properties.isAiEnabled() ? "spring-ai" : "demo");
    }
}
