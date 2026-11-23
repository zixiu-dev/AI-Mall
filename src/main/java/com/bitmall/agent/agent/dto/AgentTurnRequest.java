package com.bitmall.agent.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 开始一轮可重连聊天。客户端为一次发送生成请求编号，网络重试沿用该编号。 */
@Getter
@Setter
public class AgentTurnRequest {
  private Long userId;

  @Size(max = 40)
  private String conversationId;

  @NotBlank
  @Pattern(regexp = "[a-zA-Z0-9_-]{1,64}")
  private String clientRequestId;

  @NotBlank
  private String message;

  public long resolvedUserId() {
    return userId == null ? 10001L : userId;
  }
}
