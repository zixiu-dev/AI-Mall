package com.bitmall.agent.agent.service.trace;

import com.bitmall.agent.agent.dao.entity.AgentExecutionDetailDO;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** 一轮后台回答的诊断上下文。仅在该轮的工作线程中分配序号和累积工具次数。 */
public final class AgentTurnTrace {
  private final long turnId;
  private final Consumer<AgentExecutionDetailDO> publish;
  private int nextSequence;
  private Step currentStep;

  public AgentTurnTrace(long turnId, Consumer<AgentExecutionDetailDO> publish) {
    this.turnId = turnId;
    this.publish = publish;
  }

  /** 排队期间没有模型调用，仍可用该事件查看后台线程的等待时间。 */
  public Entry beginQueueWait() {
    return begin("QUEUE_WAIT", null);
  }

  /** 模型和工具在同一工作线程顺序执行，共用当前步骤的尝试号和来源调用序号。 */
  public Step step(int stepNo) {
    if (currentStep == null || currentStep.stepNo != stepNo) {
      currentStep = new Step(stepNo);
    }
    return currentStep;
  }

  public void confirmationRecovered(String actionId) {
    begin("CONFIRMATION_RECOVERED", null).actionId(actionId).finish("SUCCESS", null);
  }

  private Entry begin(String type, Integer stepNo) {
    return new Entry(AgentExecutionDetailDO.builder()
        .turnId(turnId).sequenceNo(++nextSequence).itemType(type).stepNo(stepNo)
        .startedAt(LocalDateTime.now(ZoneOffset.UTC)), nextSequence);
  }

  /** 一次主循环迭代。记录实际进入模型传输的尝试，也为工具保留来源调用序号。 */
  public final class Step {
    private final int stepNo;
    private int attemptNo;
    private Integer successfulModelCall;

    private Step(int stepNo) {
      this.stepNo = stepNo;
    }

    public Entry startModelCall(String candidate, String model) {
      Entry entry = begin("MODEL_CALL", stepNo);
      entry.row.attemptNo(++attemptNo).candidate(candidate).model(model);
      return entry;
    }

    public void modelSucceeded(Entry entry) {
      successfulModelCall = entry.sequence;
    }

    public void modelSkipped(String candidate) {
      Entry event = begin("MODEL_SKIPPED", stepNo);
      event.row.candidate(candidate);
      event.finish("SKIPPED", "CIRCUIT_UNAVAILABLE");
    }

    public Entry startTool(String toolCallId, String toolName) {
      Entry entry = begin("TOOL_CALL", stepNo);
      entry.row.modelCallSequence(successfulModelCall).toolName(toolName).executionCount(0);
      if (toolCallId != null && toolCallId.matches("[A-Za-z0-9:_-]{1,128}")) {
        entry.row.toolCallId(toolCallId);
      }
      return entry;
    }
  }

  /** 一个已开始的诊断动作，完成后发布一次只含有限字段的快照。 */
  public final class Entry {
    private final AgentExecutionDetailDO.AgentExecutionDetailDOBuilder row;
    private final int sequence;
    private final long startedNanos = System.nanoTime();
    private int executionCount;
    private boolean finished;

    private Entry(AgentExecutionDetailDO.AgentExecutionDetailDOBuilder row, int sequence) {
      this.row = row;
      this.sequence = sequence;
    }

    public void markExecutionStarted() {
      row.executionCount(++executionCount);
    }

    public Entry actionId(String actionId) {
      if (actionId != null && actionId.matches("[0-9a-fA-F]{32}")) {
        row.actionId(actionId);
      }
      return this;
    }

    public void modelUsage(Integer inputTokens, Integer outputTokens) {
      row.inputTokens(inputTokens).outputTokens(outputTokens);
    }

    public void firstText(long milliseconds) {
      row.firstTextMs(milliseconds);
    }

    public void finish(String status, String errorCode) {
      if (finished) {
        return;
      }
      finished = true;
      publish.accept(row.status(status).errorCode(errorCode)
          .durationMs(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos)).build());
    }
  }
}
