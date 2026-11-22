package com.bitmall.agent.agent.controller;

import com.bitmall.agent.agent.dto.ConfirmRequest;
import com.bitmall.agent.agent.service.action.AgentActionService;
import com.bitmall.agent.agent.vo.AgentReplyVO;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 用户确认或取消操作的 HTTP 入口。 先校验请求格式，再交给 AgentActionService 处理，不在 Controller 中直接执行订单 SQL。 */
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentActionController {
    /** 处理用户点击确认或取消后的请求，并返回实际执行结果。 */
    private final AgentActionService actions;

    /** 接收用户点击确认卡后的明确决定，转交确认服务处理。 */
    @PostMapping("/actions/{actionId}/confirm")
    public AgentReplyVO confirm(
            @PathVariable String actionId, @Valid @RequestBody ConfirmRequest request) {
        return actions.confirm(request.resolvedUserId(), actionId, request.getApproved());
    }
}
