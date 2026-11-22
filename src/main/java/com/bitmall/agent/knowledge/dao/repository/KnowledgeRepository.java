package com.bitmall.agent.knowledge.dao.repository;

import com.bitmall.agent.common.persistence.RowValues;
import com.bitmall.agent.knowledge.dao.mapper.KnowledgeMapper;
import com.bitmall.agent.knowledge.vo.KnowledgeHitVO;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 政策知识文档的关键词检索（不是向量检索）。
 *
 * <p>先按品牌和文档类别精确缩小范围，再在范围内按关键词打分。关键词都作为绑定参数传入 SQL。
 */
@Repository
public class KnowledgeRepository {
  /** 启用文档数的上限；超过说明数据规模已不适合这种小库全量扫描的做法。 */
  private static final int MAX_ENABLED_DOCUMENTS = 10000;

  /** 单次检索最多使用的关键词数。 */
  private static final int MAX_PATTERNS = 8;

  /** 用户输入里出现这些售后领域词时，直接作为关键词。 */
  private static final List<String> DOMAIN_WORDS =
      List.of(
          "退货", "退款", "换货", "无理由", "激活", "拆封", "到账", "配送", "运费", "包邮", "发货", "取消", "保修", "维修",
          "优惠券", "定制");

  /** 用户输入按空白和中英文标点切词。 */
  private static final String WORD_SEPARATORS = "\\s+|[，。！？、,.!?]";

  /** 关键词检索 SQL、启用文档列表，以及锁定知识源（防止重建索引期间文档被修改）。 */
  private final KnowledgeMapper mapper;

  /** 用编程式事务包住"锁定知识源 + 执行回调"，让行锁持续到回调结束。 */
  private final TransactionTemplate transaction;

  public KnowledgeRepository(KnowledgeMapper mapper, PlatformTransactionManager manager) {
    this.transaction = new TransactionTemplate(manager);
    this.mapper = mapper;
  }

  /**
   * 在锁住知识源的事务里执行 work，保证重建索引期间读到的文档快照不变。
   *
   * <p>调用时 embedding 等网络请求应已完成，不要在持锁期间发外部请求。这个事务只在业务主库上； 向量库（PG）的发布由它自己的事务提交，两边不组成跨库事务。
   */
  public void withLockedSource(Runnable work) {
    transaction.executeWithoutResult(
        status -> {
          mapper.lockSource();
          work.run();
        });
  }

  /**
   * 在指定品牌和文档类别内做关键词检索。品牌、类别必须精确匹配，即使得分很低也不会退到其他品牌去找。
   *
   * @param limit 返回条数，会被限制在 1～10
   */
  public List<KnowledgeHitVO> search(String query, String brand, String type, int limit) {
    Map<String, Object> args = new LinkedHashMap<>();
    args.put("brand", brand);
    args.put("documentType", type);
    args.put("limit", Math.min(10, Math.max(1, limit)));
    args.put("patterns", patterns(query));
    return mapper.searchSql(args).stream().map(this::toHit).toList();
  }

  /** 读取全部启用文档，供离线重建索引使用；不能拿来查询订单等业务事实。 */
  public List<KnowledgeHitVO> listEnabled() {
    List<Map<String, Object>> rows = mapper.listEnabled();
    if (rows.size() > MAX_ENABLED_DOCUMENTS) {
      throw new IllegalStateException("政策源数据超出小库上限，请使用专门检索存储");
    }
    return rows.stream().map(this::toHit).toList();
  }

  /**
   * 把用户输入转成 LIKE 匹配模式：命中的售后领域词 + 输入切出的 2～12 字词组，最多 8 个。 一个都没有时，取输入前 12 个字符兜底。
   *
   * <p>用户输入中的 % 和 _ 会被转义，按字面匹配。
   */
  public static List<String> patterns(String query) {
    Set<String> tokens = new LinkedHashSet<>();
    for (String word : DOMAIN_WORDS) {
      if (query.contains(word)) {
        tokens.add(word);
      }
    }
    for (String word : query.trim().split(WORD_SEPARATORS)) {
      if (word.length() >= 2 && word.length() <= 12) {
        tokens.add(word);
      }
    }
    if (tokens.isEmpty()) {
      tokens.add(query.substring(0, Math.min(12, query.length())));
    }
    return tokens.stream().limit(MAX_PATTERNS).map(token -> "%" + escapeLike(token) + "%").toList();
  }

  /** 转义 LIKE 通配符（SQL 里用 ESCAPE '!'）。 */
  private static String escapeLike(String value) {
    return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
  }

  private KnowledgeHitVO toHit(Map<String, Object> row) {
    return new KnowledgeHitVO(
        RowValues.longValue(row, "id"),
        RowValues.string(row, "category"),
        RowValues.string(row, "title"),
        RowValues.string(row, "content"),
        RowValues.intValue(row, "score"),
        RowValues.string(row, "brand_code"),
        RowValues.string(row, "document_type"),
        RowValues.string(row, "document_key"),
        RowValues.string(row, "source_url"),
        RowValues.string(row, "source_name"),
        RowValues.string(row, "source_checked_at"),
        RowValues.string(row, "applicable_channel"),
        RowValues.string(row, "region"),
        RowValues.string(row, "content_version"),
        RowValues.string(row, "scope_note"));
  }
}
