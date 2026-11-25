package com.bitmall.agent.agent.dao.repository;

import com.bitmall.agent.agent.dao.entity.AgentExecutionDetailDO;
import com.bitmall.agent.agent.dao.mapper.AgentExecutionDetailMapper;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 从两张诊断表读写单轮数据。序号跨表共用，查询按动作开始顺序返回。 */
@Repository
@RequiredArgsConstructor
public class AgentExecutionDetailRepository {
  /** 映射模型请求、工具执行和其他轮次事件。 */
  private final AgentExecutionDetailMapper mapper;

  public void writeModelCalls(List<AgentExecutionDetailDO> calls) {
    if (!calls.isEmpty()) {
      mapper.insertModelCalls(calls);
    }
  }

  public void writeItems(List<AgentExecutionDetailDO> items) {
    if (!items.isEmpty()) {
      mapper.insertItems(items);
    }
  }

  public List<AgentExecutionDetailDO> find(long turnId, LocalDateTime cutoff) {
    return Stream.concat(mapper.modelCalls(turnId, cutoff).stream(), mapper.items(turnId, cutoff).stream())
        .sorted(Comparator.comparingInt(AgentExecutionDetailDO::getSequenceNo))
        .toList();
  }

  /** 每次每表最多清理 limit 条。旧诊断记录不参与业务表的状态或引用约束。 */
  public void prune(LocalDateTime cutoff, int limit) {
    List<Long> modelIds = mapper.expiredModelCalls(cutoff, limit);
    if (!modelIds.isEmpty()) {
      mapper.deleteModelCalls(modelIds);
    }
    List<Long> itemIds = mapper.expiredItems(cutoff, limit);
    if (!itemIds.isEmpty()) {
      mapper.deleteItems(itemIds);
    }
  }
}
