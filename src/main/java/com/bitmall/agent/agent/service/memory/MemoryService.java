package com.bitmall.agent.agent.service.memory;

import com.bitmall.agent.agent.dao.entity.UserMemoryDO;
import com.bitmall.agent.agent.dao.repository.MemoryRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/** 读取和保存用户明确表达的长期购物偏好。 它不保存临时库存、价格或订单状态，这些易变化信息应重新通过业务工具查询。 */
@Service
public class MemoryService {
  /** 依赖组件：用户偏好表的数据访问组件。 */
  private final MemoryRepository mapper;

  /** 由 Spring 创建本组件时注入所需依赖。 这里只保存依赖，具体业务在下面的方法中执行。 */
  public MemoryService(MemoryRepository mapper) {
    this.mapper = mapper;
  }

  /** 读取指定用户显式保存的购物偏好，供系统提示补充背景。 */
  public List<UserMemoryDO> findByUser(long userId) {
    return mapper.findByUser(userId);
  }

  /** 新增或更新某个偏好键，并记录来源会话；调用入口负责保证用户明确要求保存。 */
  public void upsert(long userId, String key, String value, String conversationId) {
    mapper.upsert(userId, key, value, conversationId);
  }
}
