package com.bitmall.agent.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bitmall.agent.agent.dao.entity.ConversationEntity;
import com.bitmall.agent.agent.dao.mapper.ConversationMapper;
import com.bitmall.agent.order.dao.mapper.OrderMapper;
import java.util.Map;
import java.util.UUID;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 验证真正使用 MP SqlSession，并且基础 CRUD 与 XML 更新加入同一 Spring 事务。 */
@com.bitmall.agent.support.LegacyCatalogFixture
@SpringBootTest
@ActiveProfiles("demo")
class MybatisPersistenceIntegrationTest {
  @Autowired SqlSessionFactory factory;
  @Autowired ConversationMapper conversations;
  @Autowired OrderMapper orders;
  @Autowired com.bitmall.agent.product.dao.mapper.ProductMapper products;
  @Autowired PlatformTransactionManager transactions;

  @Test
  void usesMybatisPlusAndAvoidsSessionCachedLockReads() {
    assertThat(factory.getConfiguration()).isInstanceOf(MybatisConfiguration.class);
    assertThat(factory.getConfiguration().getLocalCacheScope())
        .isEqualTo(LocalCacheScope.STATEMENT);
    assertThat(
            factory
                .getConfiguration()
                .hasStatement("com.bitmall.agent.order.dao.mapper.OrderTraceMapper.lockSql"))
        .isTrue();
  }

  @Test
  void baseMapperAndXmlShareRollbackWithoutChangingExistingStock() {
    String id = UUID.randomUUID().toString();
    Integer before = stock();
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            status -> {
              ConversationEntity entity = new ConversationEntity();
              entity.setId(id);
              entity.setUserId(10001L);
              entity.setTitle("事务验证");
              assertThat(conversations.insert(entity)).isEqualTo(1);
              assertThat(conversations.selectById(id).getTitle()).isEqualTo("事务验证");
              assertThat(
                      conversations.selectCount(
                          new LambdaQueryWrapper<ConversationEntity>()
                              .eq(ConversationEntity::getId, id)
                              .eq(ConversationEntity::getUserId, 10001L)))
                  .isEqualTo(1);
              assertThat(orders.deductSkuStock(1001L, 1, new java.math.BigDecimal("4999.00")))
                  .isEqualTo(1);
              assertThat(stock()).isEqualTo(before - 1);
              status.setRollbackOnly();
            });
    assertThat(conversations.selectById(id)).isNull();
    assertThat(stock()).isEqualTo(before);
  }

  private Integer stock() {
    return ((Number) products.selectAvailableSku(Map.of("id", 1001L)).get(0).get("stock"))
        .intValue();
  }
}
