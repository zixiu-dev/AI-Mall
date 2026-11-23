package com.bitmall.agent.agent.dao.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 受后台执行管理的助手消息及关联问题正文，供重试核对与执行使用。 */
@Getter
@RequiredArgsConstructor
public final class AgentAnswerDO {
  private final ChatMessageDO message;
  private final String question;
}
