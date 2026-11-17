# 商品目录与 SKU 查询

## Purpose

约定页面和助手读取公共商品目录时的筛选、规格、分页与详情语义，确保用户看到的价格和规格属于同一个可核实的商品候选，避免把商品聚合展示信息当成具体规格可成交的承诺。

## Requirements

### Requirement: CAT-001 公共商品查询契约

系统 SHALL 向页面与商品工具提供一致的目录数据，并区分商品编号和可购买 SKU 编号。

#### Scenario: 详情展示具体规格

- **GIVEN** 商品存在并启用
- **WHEN** 调用 GET /api/store/products/{productId}
- **THEN** 返回商品及其可见 SKU 和属性，SKU 具有各自价格与库存。

#### Scenario: 非法或缺失商品

- **GIVEN** 输入非正数 ID，或商品不存在
- **WHEN** 查询商品详情
- **THEN** 拒绝非法参数或明确报告商品不存在，不编造其他商品替代。

### Requirement: CAT-002 同 SKU 筛选

系统 SHALL 要求预算、颜色、尺码和有货条件由同一个启用 SKU 同时满足。

#### Scenario: 不能跨规格凑条件

- **GIVEN** 黑色 41 码有货，白色 42 码有货，黑色 42 码无货
- **WHEN** 搜索黑色、42 码且有货
- **THEN** 不能用其他两个规格组合成符合要求的结果。

### Requirement: CAT-003 稳定筛选与分页

系统 SHALL 校验搜索条件和排序白名单，并返回有总数、页码、页大小、后续页标志的结果；空结果不静默放宽条件。

#### Scenario: 完整条件参与查询

- **GIVEN** 用户指定预算、属性、排序和分页
- **WHEN** 调用 GET /api/store/products
- **THEN** 按全部有效条件排序后分页，展示项价格与规格来自同一匹配 SKU。

#### Scenario: 获取筛选选项

- **WHEN** 调用 GET /api/store/product-filters 并可指定品类
- **THEN** 返回可选属性，不承诺任意选项组合都有可售 SKU。

### Requirement: CAT-004 真实商品来源与目录替换

系统 SHALL 为启用的真实品牌商品保存具体型号、规格、人民币参考价格、来源链接、价格口径与核对日期；替换演示目录使用独立商品和 SKU，旧商品下架且历史订单快照、库存及 trace 不被改写。

#### Scenario: 真实规格可核对

- **WHEN** 查询真实商品详情
- **THEN** 型号、规格与报价有官网或品牌资料来源，区分官网标价与历史发布价，库存明确属于本项目

#### Scenario: 旧商品安全退场

- **WHEN** 已有演示数据的数据库执行新迁移
- **THEN** 原商品不再作为启用商品售卖，历史订单名称、价格、库存及关联标识保持原值，新订单使用新 SKU

## Current limitations

当前为数据库关键词与属性查询，没有全文搜索引擎、通用中文分词或个性化推荐；结果库存可为多个匹配 SKU 的合计，购买必须选择具体 SKU。

## References

[设计与流程](../../../docs/product-design.md) · [验证入口](../../../src/test/java/com/bitmall/agent/product/controller/CatalogSearchIntegrationTest.java)
