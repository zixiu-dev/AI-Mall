package com.bitmall.agent.agent.controller;

import com.bitmall.agent.agent.dto.AgentTurnRequest;
import com.bitmall.agent.agent.service.chat.AgentTurnService;
import com.bitmall.agent.agent.vo.AgentExecutionVO;
import com.bitmall.agent.agent.vo.AgentTurnVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 后台聊天的提交、查询与订阅入口。重新订阅按原助手消息读取结果。 */
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentTurnController {
  /** 维护后台执行生命周期与回答订阅。 */
  private final AgentTurnService turns;

  @PostMapping("/chat/turns")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public AgentTurnVO submit(@RequestBody @Valid AgentTurnRequest request) {
    return turns.submit(request);
  }

  @GetMapping("/chat/turns/{clientRequestId}")
  public AgentTurnVO turn(
      @PathVariable @Pattern(regexp = "[a-zA-Z0-9_-]{1,64}") String clientRequestId,
      @RequestParam(defaultValue = "10001") long userId) {
    return turns.resultByRequest(userId, clientRequestId);
  }

  @GetMapping("/messages/{messageId}")
  public AgentTurnVO answer(
      @PathVariable long messageId, @RequestParam(defaultValue = "10001") long userId) {
    return turns.result(userId, messageId);
  }

  /** 查询按发生顺序排列的执行明细，仅限该回答的所属用户。 */
  @GetMapping("/messages/{messageId}/execution")
  public AgentExecutionVO execution(
      @PathVariable long messageId, @RequestParam(defaultValue = "10001") long userId) {
    return turns.executionDetails(userId, messageId);
  }

  @GetMapping(value = "/messages/{messageId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream(
      @PathVariable long messageId, @RequestParam(defaultValue = "10001") long userId) {
    return turns.subscribe(userId, messageId);
  }
}
