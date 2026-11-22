package com.bitmall.agent.knowledge.service;

import com.bitmall.agent.knowledge.vo.KnowledgeIntentVO;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * 识别问题中的品牌、政策类别和购买渠道，并校验调用方（HTTP 或 Agent 模型）传来的结构化筛选条件。
 *
 * <p>这里只做基于关键词和正则的确定性识别与冲突检查：复杂的自然语言表达交给外层 ReAct 模型提取参数，
 * 本类不会再调用一次 LLM，也不涉及任何写操作。筛选条件与用户原文矛盾时返回"需要补问"的意图，
 * 而不是擅自选其中一边。
 */
@Service
public class KnowledgeIntentService {
  /** 品牌编码 → 识别正则。英文名前后要求不是字母，避免 "mac" 误中 "machine" 之类的单词。 */
  private static final Map<String, Pattern> BRANDS = new LinkedHashMap<>();

  /** 政策类别编码 → 触发关键词（匹配前问题会转成小写）。 */
  private static final Map<String, List<String>> TYPES = new LinkedHashMap<>();

  private static final String BRAND_PLATFORM = "PLATFORM";
  private static final String TYPE_ORDER = "ORDER";
  private static final String TYPE_WARRANTY = "WARRANTY";

  private static final String SCOPE_AUTO = "AUTO";
  private static final String SCOPE_BRAND = "BRAND";
  private static final String SCOPE_PLATFORM = "PLATFORM";
  private static final String SCOPE_UNRESOLVED = "UNRESOLVED";

  /** 表示"在本商城购买"的说法。 */
  private static final String[] PLATFORM_WORDS = {
    "本商城", "本平台", "本站", "沃沃", "你们商城", "咱们商城", "这里买", "你们这里", "你们这买", "这个商城"
  };

  /** 作为 brand 筛选值时等同于 PLATFORM 的中文写法。 */
  private static final List<String> PLATFORM_BRAND_ALIASES = List.of("本平台", "沃沃", "本商城");

  /** 明确在问取消或付款的词，用于判断 ORDER 类别是否真的被问到。 */
  private static final String[] ORDER_ACTION_WORDS = {"取消", "支付", "付款", "cancel", "payment"};

  /** 已知但尚未收录政策的品牌。 */
  private static final Pattern UNSUPPORTED_BRAND =
      Pattern.compile(
          "(?i)华为|三星|索尼|戴森|联想|罗技|美的|海尔|(?<![a-z])(?:huawei|samsung|sony|dyson|lenovo|logitech|adidas)(?![a-z])");

  /** 中国大陆以外、尚未收录资料的地区。 */
  private static final Pattern OUTSIDE_SUPPORTED_REGION =
      Pattern.compile("(?i)美国|美区|日本|日区|香港|澳门|台湾|英国|新加坡|united states|singapore");

  /** 看起来在指某个品牌：英文单词紧跟售后类词语，或"某某品牌/官网/官方"。 */
  private static final Pattern UNKNOWN_BRAND_SHAPE =
      Pattern.compile(
          "(?i)[a-z][a-z0-9-]{1,24}\\s*(?:的)?(?:退货|退款|保修|配送|售后)|[\\p{IsHan}a-z0-9]{2,16}(?:品牌|官网|官方)");

  /** 泛指多个品牌的说法，不当作某个陌生品牌。 */
  private static final String MULTIPLE_BRANDS_PHRASE = ".*(?:几个|多个|五个|各个|这些)品牌.*";

  static {
    BRANDS.put(
        "APPLE",
        Pattern.compile(
            "(?i)(?<![a-z])(?:apple|iphone|ipad|macbook(?:\\s?(?:air|pro))?|airpods(?:\\s?pro)?|imac|mac\\s?mini|mac)(?![a-z])|苹果(?!汁|味|越桔|水果|酱|干)"));
    BRANDS.put("XIAOMI", Pattern.compile("(?i)(?<![a-z])(?:xiaomi|redmi)(?![a-z])|小米|米家|红米"));
    BRANDS.put("NIKE", Pattern.compile("(?i)(?<![a-z])(?:nike|pegasus)(?![a-z])|耐克"));
    BRANDS.put("IKEA", Pattern.compile("(?i)(?<![a-z])ikea(?![a-z])|宜家|波昂|斯黛纳|特提亚"));
    BRANDS.put("MUJI", Pattern.compile("(?i)(?<![a-z])muji(?![a-z])|无印良品|無印良品"));
    TYPES.put(
        "RETURNS",
        List.of(
            "退货",
            "退款",
            "换货",
            "退换",
            "能退",
            "退掉",
            "退回",
            "退钱",
            "无理由",
            "可以退",
            "可退",
            "退吗",
            "退么",
            "怎么退",
            "return",
            "refund",
            "exchange"));
    TYPES.put("REFUND", List.of("到账", "退款进度", "原路", "refund status"));
    TYPES.put("WARRANTY", List.of("保修", "质保", "维修", "三包", "warranty", "repair"));
    TYPES.put(
        "SHIPPING", List.of("配送", "运费", "包邮", "送货", "发货", "快递", "物流", "shipping", "delivery"));
    TYPES.put("ORDER", List.of("取消", "支付", "付款", "订单", "cancel", "payment"));
    TYPES.put("COUPON", List.of("优惠券", "领券", "满减", "coupon"));
  }

  /** 问题中明确出现的品牌（按 BRANDS 定义顺序）。不做"没品牌就算本站"的兜底，供原文约束核对和指代消解使用。 */
  public List<String> brands(String query) {
    return BRANDS.entrySet().stream()
        .filter(entry -> entry.getValue().matcher(query).find())
        .map(Map.Entry::getKey)
        .toList();
  }

  /**
   * 问题涉及的政策类别。
   *
   * <p>"订单"一词很常见，单独出现时不算在问订单规则；只有出现取消、付款或"订单规则"等说法时才保留 ORDER。
   */
  public List<String> categories(String query) {
    String text = query.toLowerCase(Locale.ROOT);
    boolean asksOrderRules = contains(text, ORDER_ACTION_WORDS) || text.contains("订单规则");
    return matchedTypes(text).stream()
        .filter(type -> !type.equals(TYPE_ORDER) || asksOrderRules)
        .toList();
  }

  /** 问题是否说明在本商城购买。 */
  public boolean platform(String query) {
    return contains(query, PLATFORM_WORDS);
  }

  /** 问题是否涉及未收录的品牌：命中已知未收录品牌，或者没识别出已收录品牌/本站但看起来在说某个品牌。 */
  public boolean unknownBrand(String query) {
    if (UNSUPPORTED_BRAND.matcher(query).find()) {
      return true;
    }
    return brands(query).isEmpty() && !platform(query) && looksLikeUnknownBrand(query);
  }

  /** 问题是否提到中国大陆以外的购买地区。 */
  public boolean outsideSupportedRegion(String query) {
    return OUTSIDE_SUPPORTED_REGION.matcher(query).find();
  }

  /**
   * 结合问题原文和显式筛选条件，得出检索意图。
   *
   * <p>显式筛选优先于从原文识别的结果；但如果筛选和用户原文明显矛盾，就返回需要补问的意图，
   * 不在两者之间任选一个。检查按以下顺序进行，命中即返回：
   * 地区 → 筛选值是否合法 → 筛选与原文是否一致 → 是否缺类别 → 是否含未收录品牌 → 渠道是否冲突 →
   * 本站规则 → 品牌是否可确认 → 品牌官方政策。
   *
   * @param brand 品牌筛选，可为空
   * @param category 政策类别筛选，可为空
   * @param scope AUTO / BRAND / PLATFORM，为空按 AUTO
   * @throws IllegalArgumentException 问题为空或过长、scope 取值非法时
   */
  public KnowledgeIntentVO resolve(String query, String brand, String category, String scope) {
    if (query == null || query.isBlank() || query.length() > 1000) {
      throw new IllegalArgumentException("检索问题应为 1～1000 个字符");
    }
    if (outsideSupportedRegion(query)) {
      return ask(brands(query), categories(query), "当前仅收录中国大陆资料，请确认购买地区，不能套用其他地区规则。");
    }
    String text = query.toLowerCase(Locale.ROOT);
    String requestedScope = scope == null ? SCOPE_AUTO : scope.toUpperCase(Locale.ROOT);
    if (!List.of(SCOPE_AUTO, SCOPE_BRAND, SCOPE_PLATFORM).contains(requestedScope)) {
      throw new IllegalArgumentException("scope 只能为 AUTO、BRAND 或 PLATFORM");
    }

    // 从原文识别品牌和类别。
    List<String> detectedBrands = new ArrayList<>(brands(query));
    List<String> detectedTypes = new ArrayList<>(matchedTypes(text));
    // "退货订单""保修订单"主要在问政策，只有明确提到取消/付款时才额外查 ORDER。
    if (detectedTypes.size() > 1 && !contains(text, ORDER_ACTION_WORDS)) {
      detectedTypes.remove(TYPE_ORDER);
    }
    boolean platformText = contains(text, PLATFORM_WORDS);

    // 筛选值本身无法识别。
    String selectedBrand = normalizeBrand(brand);
    String selectedType = normalizeType(category);
    if (brand != null && !brand.isBlank() && selectedBrand == null) {
      return ask(
          detectedBrands, detectedTypes, "未收录这个品牌。当前可查 Apple、小米、Nike、IKEA、MUJI 或本商城规则，请确认品牌。");
    }
    if (category != null && !category.isBlank() && selectedType == null) {
      return ask(detectedBrands, detectedTypes, "请说明要查退换货、保修、配送、订单还是优惠券。");
    }

    // 筛选值与原文不一致：原文提了别的品牌、或提了多个品牌；原文类别与筛选类别不同。
    boolean brandFilterConflicts =
        selectedBrand != null
            && !selectedBrand.equals(BRAND_PLATFORM)
            && !detectedBrands.isEmpty()
            && (!detectedBrands.contains(selectedBrand) || detectedBrands.size() > 1);
    if (brandFilterConflicts) {
      return ask(detectedBrands, detectedTypes, "筛选品牌与原问题不一致，请确认要查哪个品牌，或将多个品牌分别查询。");
    }
    boolean typeFilterConflicts =
        selectedType != null && !detectedTypes.isEmpty() && !detectedTypes.contains(selectedType);
    if (typeFilterConflicts) {
      return ask(detectedBrands, detectedTypes, "筛选文档类别与原问题不一致，请确认要查退换货、保修还是配送等规则。");
    }

    if (selectedType != null) {
      detectedTypes = new ArrayList<>(List.of(selectedType));
    }
    if (detectedTypes.isEmpty()) {
      return ask(detectedBrands, detectedTypes, "请再说明想了解该品牌的退换货、保修还是配送规则。");
    }
    if (UNSUPPORTED_BRAND.matcher(query).find()) {
      return ask(detectedBrands, detectedTypes, "问题包含尚未收录政策的品牌，当前不能用其他品牌规则替代。请确认需要查询的品牌或提供具体官方资料。");
    }

    // 购买渠道冲突：原文是本站购买却要求查品牌官方，或筛选为本站而原文指向品牌。
    boolean brandFilterIsPlatform = BRAND_PLATFORM.equals(selectedBrand);
    if (requestedScope.equals(SCOPE_BRAND) && (platformText || brandFilterIsPlatform)) {
      return ask(detectedBrands, detectedTypes, "问题涉及本站购买，但筛选要求品牌官方渠道，请确认购买渠道。");
    }
    boolean platformRequested = requestedScope.equals(SCOPE_PLATFORM) || brandFilterIsPlatform;
    if (platformRequested && !platformText && !detectedBrands.isEmpty()) {
      return ask(detectedBrands, detectedTypes, "原问题指向品牌政策，但筛选为本平台规则，请确认是否在沃沃智购买的商品。");
    }

    if (platformText || platformRequested) {
      return new KnowledgeIntentVO(
          List.of(BRAND_PLATFORM), detectedTypes, SCOPE_PLATFORM, false, "查询本站规则；品牌官方渠道的政策不自动适用于本站订单。");
    }

    // 以下为品牌（或未指定品牌）的情况。
    if (selectedBrand != null) {
      detectedBrands = new ArrayList<>(List.of(selectedBrand));
    }
    boolean noBrandIdentified = detectedBrands.isEmpty();
    if (noBrandIdentified && selectedBrand == null && !platformText && looksLikeUnknownBrand(query)) {
      return ask(detectedBrands, detectedTypes, "未能确认这个品牌，不能用本站或其他品牌政策替代。请说明品牌与购买渠道。");
    }
    // 保修期限因品牌和商品差异很大，没有品牌时不能给出统一答案。
    if (noBrandIdentified
        && (requestedScope.equals(SCOPE_BRAND) || detectedTypes.contains(TYPE_WARRANTY))) {
      return ask(detectedBrands, detectedTypes, "需要先确认品牌和具体商品，不能给所有品牌套用同一个保修或售后期限。");
    }
    if (noBrandIdentified) {
      return new KnowledgeIntentVO(
          List.of(BRAND_PLATFORM),
          detectedTypes,
          SCOPE_PLATFORM,
          false,
          "未指定品牌，以下仅为沃沃智购体验规则；若问品牌官方政策，请补充品牌和渠道。");
    }
    return new KnowledgeIntentVO(
        detectedBrands,
        detectedTypes,
        SCOPE_BRAND,
        false,
        "品牌官方渠道政策仅作参考，须核对购买地区、渠道和商品条件；不能直接裁决本站订单资格。");
  }

  /** 按关键词命中的类别（按 TYPES 定义顺序），不做 ORDER 的额外筛选。text 应已转为小写。 */
  private static List<String> matchedTypes(String text) {
    return TYPES.entrySet().stream()
        .filter(entry -> entry.getValue().stream().anyMatch(text::contains))
        .map(Map.Entry::getKey)
        .toList();
  }

  /**
   * 问题是否像在说一个未收录的品牌，例如"xx 的退货"或"某某官网"。
   *
   * <p>宁可多补问一次，也不把陌生品牌当成本站规则来回答；但"几个品牌""这些品牌"这类泛指不算。
   */
  private static boolean looksLikeUnknownBrand(String query) {
    if (query.matches(MULTIPLE_BRANDS_PHRASE)) {
      return false;
    }
    return UNKNOWN_BRAND_SHAPE.matcher(query).find();
  }

  /** 构造需要补问的意图，保留已识别出的品牌和类别。 */
  private static KnowledgeIntentVO ask(List<String> brands, List<String> types, String message) {
    return new KnowledgeIntentVO(brands, types, SCOPE_UNRESOLVED, true, message);
  }

  private static boolean contains(String text, String... words) {
    for (String word : words) {
      if (text.contains(word)) {
        return true;
      }
    }
    return false;
  }

  /**
   * 把 brand 筛选值归一成品牌编码：支持编码本身（大小写不敏感）、本站的中文别名，以及能唯一匹配某个品牌的写法。
   *
   * @return 无法唯一识别时返回 null
   */
  private static String normalizeBrand(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String code = value.trim().toUpperCase(Locale.ROOT);
    if (code.equals(BRAND_PLATFORM) || PLATFORM_BRAND_ALIASES.contains(value.trim())) {
      return BRAND_PLATFORM;
    }
    if (BRANDS.containsKey(code)) {
      return code;
    }
    List<String> matched =
        BRANDS.entrySet().stream()
            .filter(entry -> entry.getValue().matcher(value).find())
            .map(Map.Entry::getKey)
            .toList();
    return matched.size() == 1 ? matched.get(0) : null;
  }

  /**
   * 把 category 筛选值归一成类别编码：支持编码本身（大小写不敏感），以及只命中一个类别关键词的写法。
   *
   * @return 无法唯一识别时返回 null
   */
  private static String normalizeType(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String code = value.trim().toUpperCase(Locale.ROOT);
    if (TYPES.containsKey(code)) {
      return code;
    }
    List<String> matched =
        TYPES.entrySet().stream()
            .filter(entry -> entry.getValue().stream().anyMatch(value::contains))
            .map(Map.Entry::getKey)
            .toList();
    return matched.size() == 1 ? matched.get(0) : null;
  }
}
