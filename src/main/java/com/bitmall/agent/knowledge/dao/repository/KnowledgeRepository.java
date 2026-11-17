package com.bitmall.agent.knowledge.dao.repository;

import com.bitmall.agent.common.persistence.RowValues;
import com.bitmall.agent.knowledge.dao.mapper.KnowledgeMapper;
import com.bitmall.agent.knowledge.vo.KnowledgeHitVO;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

/** 先由品牌/类别缩小范围，再在组内按绑定参数计算关键词分数；不是向量检索。 */
@Repository
public class KnowledgeRepository {
  private final KnowledgeMapper mapper;
  private final org.springframework.transaction.support.TransactionTemplate transaction;

  public KnowledgeRepository(
      KnowledgeMapper mapper, org.springframework.transaction.PlatformTransactionManager manager) {
    this.transaction = new org.springframework.transaction.support.TransactionTemplate(manager);
    this.mapper = mapper;
  }

  /** 模型网络请求已经完成；主库行锁固定源快照，PG 自己提交发布事务，不创建跨库写事务。 */
  public void withLockedSource(Runnable work) {
    transaction.executeWithoutResult(
        status -> {
          mapper.lockSource();
          work.run();
        });
  }

  /** 服务层按分组调用本方法，品牌和类别必须精确匹配，低分时也不会跨品牌兜底。 */
  public List<KnowledgeHitVO> search(String query, String brand, String type, int limit) {
    var args = new java.util.LinkedHashMap<String, Object>();
    args.put("brand", brand);
    args.put("documentType", type);
    args.put("limit", Math.min(10, Math.max(1, limit)));
    args.put("patterns", patterns(query));
    return mapper.searchSql(args).stream().map(this::hit).toList();
  }

  /** 离线索引的启用文档快照；调用方不能用它查询订单等业务事实。 */
  public List<KnowledgeHitVO> listEnabled() {
    var rows = mapper.listEnabled();
    if (rows.size() > 10000) throw new IllegalStateException("政策源数据超出小库上限，请使用专门检索存储");
    return rows.stream().map(this::hit).toList();
  }

  /** 有限领域词加输入词组，不把任意 SQL 通配符解释为检索表达式。 */
  public static List<String> patterns(String query) {
    var tokens = new LinkedHashSet<String>();
    for (String word :
        List.of(
            "退货", "退款", "换货", "无理由", "激活", "拆封", "到账", "配送", "运费", "包邮", "发货", "取消", "保修", "维修",
            "优惠券", "定制")) if (query.contains(word)) tokens.add(word);
    for (String word : query.trim().split("\\s+|[，。！？、,.!?]"))
      if (word.length() >= 2 && word.length() <= 12) tokens.add(word);
    if (tokens.isEmpty()) tokens.add(query.substring(0, Math.min(12, query.length())));
    return tokens.stream().limit(8).map(t -> "%" + escapeLike(t) + "%").toList();
  }

  /** LIKE 通配符不应把用户输入变成任意规则匹配；感叹号是显式转义符。 */
  private static String escapeLike(String value) {
    return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
  }

  private KnowledgeHitVO hit(Map<String, Object> r) {
    return new KnowledgeHitVO(
        RowValues.longValue(r, "id"),
        RowValues.string(r, "category"),
        RowValues.string(r, "title"),
        RowValues.string(r, "content"),
        RowValues.intValue(r, "score"),
        RowValues.string(r, "brand_code"),
        RowValues.string(r, "document_type"),
        RowValues.string(r, "document_key"),
        RowValues.string(r, "source_url"),
        RowValues.string(r, "source_name"),
        RowValues.string(r, "source_checked_at"),
        RowValues.string(r, "applicable_channel"),
        RowValues.string(r, "region"),
        RowValues.string(r, "content_version"),
        RowValues.string(r, "scope_note"));
  }
}
