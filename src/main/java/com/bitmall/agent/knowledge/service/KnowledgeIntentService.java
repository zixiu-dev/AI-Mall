package com.bitmall.agent.knowledge.service;

import com.bitmall.agent.knowledge.vo.KnowledgeIntentVO;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** 识别品牌与政策类别，并校验模型传来的结构化筛选。这里只做确定性归一化与冲突处理， 复杂表达可由已有 ReAct 模型提取参数，不额外调用一次 LLM，不授权任何写操作。 */
@Service
public class KnowledgeIntentService {
  private static final Map<String, String> BRANDS = new LinkedHashMap<>();
  private static final Map<String, List<String>> TYPES = new LinkedHashMap<>();

  static {
    BRANDS.put(
        "APPLE",
        "(?i)(?<![a-z])(?:apple|iphone|ipad|macbook(?:\\s?(?:air|pro))?|airpods(?:\\s?pro)?|imac|mac\\s?mini|mac)(?![a-z])|苹果(?!汁|味|越桔|水果|酱|干)");
    BRANDS.put("XIAOMI", "(?i)(?<![a-z])(?:xiaomi|redmi)(?![a-z])|小米|米家|红米");
    BRANDS.put("NIKE", "(?i)(?<![a-z])(?:nike|pegasus)(?![a-z])|耐克");
    BRANDS.put("IKEA", "(?i)(?<![a-z])ikea(?![a-z])|宜家|波昂|斯黛纳|特提亚");
    BRANDS.put("MUJI", "(?i)(?<![a-z])muji(?![a-z])|无印良品|無印良品");
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

  /** 原文约束提取不使用默认平台兜底，供子问题边界和上下文消歧使用。 */
  public List<String> brands(String query) {
    return BRANDS.entrySet().stream()
        .filter(e -> Pattern.compile(e.getValue()).matcher(query).find())
        .map(Map.Entry::getKey)
        .toList();
  }

  public List<String> categories(String query) {
    String text = query.toLowerCase(Locale.ROOT);
    return TYPES.entrySet().stream()
        .filter(e -> e.getValue().stream().anyMatch(text::contains))
        .map(Map.Entry::getKey)
        .filter(
            t ->
                !t.equals("ORDER") || contains(text, "取消", "支付", "付款", "cancel", "payment", "订单规则"))
        .toList();
  }

  public boolean platform(String query) {
    return contains(query, "本商城", "本平台", "本站", "沃沃", "你们商城", "咱们商城", "这里买", "你们这里", "你们这买", "这个商城");
  }

  public boolean unknownBrand(String query) {
    return Pattern.compile(
                "(?i)华为|三星|索尼|戴森|联想|罗技|美的|海尔|(?<![a-z])(?:huawei|samsung|sony|dyson|lenovo|logitech|adidas)(?![a-z])")
            .matcher(query)
            .find()
        || (brands(query).isEmpty() && !platform(query) && looksLikeUnknownBrand(query));
  }

  public boolean outsideSupportedRegion(String query) {
    return Pattern.compile("(?i)美国|美区|日本|日区|香港|澳门|台湾|英国|新加坡|united states|singapore")
        .matcher(query)
        .find();
  }

  /** 显式约束优先，但若与用户明确文字矛盾，必须停下来补问而不是任选一边。 */
  public KnowledgeIntentVO resolve(String query, String brand, String category, String scope) {
    if (query == null || query.isBlank() || query.length() > 1000)
      throw new IllegalArgumentException("检索问题应为 1～1000 个字符");
    if (outsideSupportedRegion(query))
      return ask(brands(query), categories(query), "当前仅收录中国大陆资料，请确认购买地区，不能套用其他地区规则。");
    String text = query.toLowerCase(Locale.ROOT),
        requestedScope = scope == null ? "AUTO" : scope.toUpperCase(Locale.ROOT);
    if (!List.of("AUTO", "BRAND", "PLATFORM").contains(requestedScope))
      throw new IllegalArgumentException("scope 只能为 AUTO、BRAND 或 PLATFORM");
    var detectedBrands = new ArrayList<String>();
    for (var entry : BRANDS.entrySet())
      if (Pattern.compile(entry.getValue()).matcher(query).find())
        detectedBrands.add(entry.getKey());
    var detectedTypes = new ArrayList<String>();
    for (var entry : TYPES.entrySet())
      if (entry.getValue().stream().anyMatch(text::contains)) detectedTypes.add(entry.getKey());
    // “退货订单”“保修订单”主要在问政策，只有明确取消/付款才额外查 ORDER。
    if (detectedTypes.size() > 1 && !contains(text, "取消", "支付", "付款", "cancel", "payment"))
      detectedTypes.remove("ORDER");
    boolean platformText =
        contains(text, "本商城", "本平台", "本站", "沃沃", "你们商城", "咱们商城", "这里买", "你们这里", "你们这买", "这个商城");
    String selectedBrand = normalizeBrand(brand), selectedType = normalizeType(category);
    if (brand != null && !brand.isBlank() && selectedBrand == null)
      return ask(
          detectedBrands, detectedTypes, "未收录这个品牌。当前可查 Apple、小米、Nike、IKEA、MUJI 或本商城规则，请确认品牌。");
    if (category != null && !category.isBlank() && selectedType == null)
      return ask(detectedBrands, detectedTypes, "请说明要查退换货、保修、配送、订单还是优惠券。");
    if (selectedBrand != null
        && !selectedBrand.equals("PLATFORM")
        && !detectedBrands.isEmpty()
        && (!detectedBrands.contains(selectedBrand) || detectedBrands.size() > 1))
      return ask(detectedBrands, detectedTypes, "筛选品牌与原问题不一致，请确认要查哪个品牌，或将多个品牌分别查询。");
    if (selectedType != null && !detectedTypes.isEmpty() && !detectedTypes.contains(selectedType))
      return ask(detectedBrands, detectedTypes, "筛选文档类别与原问题不一致，请确认要查退换货、保修还是配送等规则。");
    if (selectedType != null) detectedTypes = new ArrayList<>(List.of(selectedType));
    if (detectedTypes.isEmpty())
      return ask(detectedBrands, detectedTypes, "请再说明想了解该品牌的退换货、保修还是配送规则。");
    if (Pattern.compile(
            "(?i)华为|三星|索尼|戴森|联想|罗技|美的|海尔|(?<![a-z])(?:huawei|samsung|sony|dyson|lenovo|logitech|adidas)(?![a-z])")
        .matcher(query)
        .find())
      return ask(detectedBrands, detectedTypes, "问题包含尚未收录政策的品牌，当前不能用其他品牌规则替代。请确认需要查询的品牌或提供具体官方资料。");
    if (requestedScope.equals("BRAND") && (platformText || "PLATFORM".equals(selectedBrand)))
      return ask(detectedBrands, detectedTypes, "问题涉及本站购买，但筛选要求品牌官方渠道，请确认购买渠道。");
    if ((requestedScope.equals("PLATFORM") || "PLATFORM".equals(selectedBrand))
        && !platformText
        && !detectedBrands.isEmpty())
      return ask(detectedBrands, detectedTypes, "原问题指向品牌政策，但筛选为本平台规则，请确认是否在沃沃智购买的商品。");
    boolean platform =
        platformText || requestedScope.equals("PLATFORM") || "PLATFORM".equals(selectedBrand);
    if (platform)
      return new KnowledgeIntentVO(
          List.of("PLATFORM"), detectedTypes, "PLATFORM", false, "查询本站规则；品牌官方渠道的政策不自动适用于本站订单。");
    if (selectedBrand != null) detectedBrands = new ArrayList<>(List.of(selectedBrand));
    if (detectedBrands.isEmpty()
        && selectedBrand == null
        && !platformText
        && looksLikeUnknownBrand(query))
      return ask(detectedBrands, detectedTypes, "未能确认这个品牌，不能用本站或其他品牌政策替代。请说明品牌与购买渠道。");
    if (detectedBrands.isEmpty()
        && (requestedScope.equals("BRAND") || detectedTypes.contains("WARRANTY")))
      return ask(detectedBrands, detectedTypes, "需要先确认品牌和具体商品，不能给所有品牌套用同一个保修或售后期限。");
    if (detectedBrands.isEmpty())
      return new KnowledgeIntentVO(
          List.of("PLATFORM"),
          detectedTypes,
          "PLATFORM",
          false,
          "未指定品牌，以下仅为沃沃智购体验规则；若问品牌官方政策，请补充品牌和渠道。");
    return new KnowledgeIntentVO(
        detectedBrands,
        detectedTypes,
        "BRAND",
        false,
        "品牌官方渠道政策仅作参考，须核对购买地区、渠道和商品条件；不能直接裁决本站订单资格。");
  }

  /** 对明确“某品牌/官网”或英文品牌前缀保守补问，不把陌生品牌当成平台。 */
  private static boolean looksLikeUnknownBrand(String query) {
    if (query.matches(".*(?:几个|多个|五个|各个|这些)品牌.*")) return false;
    return Pattern.compile(
            "(?i)[a-z][a-z0-9-]{1,24}\\s*(?:的)?(?:退货|退款|保修|配送|售后)|[\\p{IsHan}a-z0-9]{2,16}(?:品牌|官网|官方)")
        .matcher(query)
        .find();
  }

  private static KnowledgeIntentVO ask(List<String> brands, List<String> types, String message) {
    return new KnowledgeIntentVO(brands, types, "UNRESOLVED", true, message);
  }

  private static boolean contains(String text, String... words) {
    for (String word : words) if (text.contains(word)) return true;
    return false;
  }

  private static String normalizeBrand(String value) {
    if (value == null || value.isBlank()) return null;
    String v = value.trim().toUpperCase(Locale.ROOT);
    if (v.equals("PLATFORM") || List.of("本平台", "沃沃", "本商城").contains(value.trim()))
      return "PLATFORM";
    if (BRANDS.containsKey(v)) return v;
    var found =
        BRANDS.entrySet().stream()
            .filter(e -> Pattern.compile(e.getValue()).matcher(value).find())
            .map(Map.Entry::getKey)
            .toList();
    return found.size() == 1 ? found.get(0) : null;
  }

  private static String normalizeType(String value) {
    if (value == null || value.isBlank()) return null;
    String v = value.trim().toUpperCase(Locale.ROOT);
    if (TYPES.containsKey(v)) return v;
    var found =
        TYPES.entrySet().stream()
            .filter(e -> e.getValue().stream().anyMatch(value::contains))
            .map(Map.Entry::getKey)
            .toList();
    return found.size() == 1 ? found.get(0) : null;
  }
}
