package com.bitmall.agent.agent.controller;

import com.bitmall.agent.agent.config.AgentProperties;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 仅公开执行模式；不泄露模型凭证，也不把配置状态当作远程模型健康检查。 */
@RestController
@RequestMapping("/api/agent")
public class AgentStatusController {
    private final AgentProperties properties;

    public AgentStatusController(AgentProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/status")
    public Map<String, String> status() {
        return Map.of("mode", properties.isAiEnabled() ? "spring-ai" : "demo");
    }
}
