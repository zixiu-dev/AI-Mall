package com.bitmall.agent.agent.service.tool.product;

import static com.bitmall.agent.agent.service.tool.RegisteredTool.Risk.READ;
import static com.bitmall.agent.agent.service.tool.support.ToolArguments.*;
import static com.bitmall.agent.agent.service.tool.support.ToolDefinitions.*;

import com.bitmall.agent.agent.service.tool.RegisteredTool;
import com.bitmall.agent.agent.service.tool.ToolProvider;
import com.bitmall.agent.product.dto.ProductSearchRequest;
import com.bitmall.agent.product.service.ProductService;

import org.springframework.stereotype.Component;

import java.util.List;

/** 向 Agent 提供商品搜索和详情工具。 工具描述和参数 Schema 在这里声明，真正的数据查询仍调用 ProductService。 */
@Component
public class ProductTools implements ToolProvider {
    /** 本功能声明的工具列表；保存的是函数定义，声明时不会直接执行交易。 */
    private final List<RegisteredTool> tools;

    /** 启动时声明本模块的工具名称、参数格式、执行函数和预览函数。这里只保存函数定义；模型实际调用或用户确认后才会执行函数。 */
    public ProductTools(ProductService catalog) {
        tools =
                List.of(
                        tool(
                                "product_search",
                                "结构化检索商品：将需求拆为关键词、品类、预算、品牌和规格。用户明确预算必须传"
                                    + " maxPrice，不允许省略；每次重查和翻页保留原硬条件。价格、颜色、尺码、库存会匹配同一个 SKU；返回"
                                    + " items、total、hasMore、filters、matchedSkuId/matchedSpec。先少量召回再查详情；无结果先解释条件，不可擅自提高预算或替换尺码。query"
                                    + " 不支持 SKU ID 定位。",
                                """
{
  "type": "object",
  "properties": {
    "query": {
      "type": "string",
      "description": "仅商品关键词，多个词用空格分开，例如 跑鞋 缓震；不要把预算、尺码或整句需求放这里。支持少量同义词，非语义向量搜索。",
      "maxLength": 120
    },
    "category": {
      "type": "string",
      "description": "商城品类：电脑办公、数码影音、运动户外、生活家电、智能穿戴、居家生活、食品饮料、个护美妆"
    },
    "brand": {
      "type": "string",
      "description": "品牌精确筛选，忽略英文大小写，例如 Apple、Nike 或米家"
    },
    "minPrice": {
      "type": "number",
      "minimum": 0,
      "description": "最低 SKU 商品单价，单位元，非券后价"
    },
    "maxPrice": {
      "type": "number",
      "minimum": 0,
      "description": "最高 SKU 商品单价，单位元。用户预算上限必须传入，不能推荐超预算商品"
    },
    "color": {
      "type": "string",
      "description": "标准颜色，如 黑色、白色、灰色、蓝色、绿色"
    },
    "size": {
      "type": "string",
      "description": "鞋码如 42，不要猜测用户尺码"
    },
    "material": {
      "type": "string",
      "description": "材质，如 网布、纯棉、尼龙"
    },
    "useCase": {
      "type": "string",
      "description": "主要用途，如 跑步、通勤、办公、徒步"
    },
    "inStock": {
      "type": "boolean",
      "description": "是否仅返回有货 SKU，购买推荐时设为 true"
    },
    "sort": {
      "type": "string",
      "enum": [
        "recommended",
        "sales",
        "priceAsc",
        "priceDesc",
        "rating"
      ],
      "description": "相关性推荐/销量/价格升序/价格降序/评分"
    },
    "page": {
      "type": "integer",
      "minimum": 1,
      "maximum": 10000,
      "description": "从1开始，用户要求更多时翻页"
    },
    "limit": {
      "type": "integer",
      "minimum": 1,
      "maximum": 30,
      "description": "默认4，推荐先取3~4个，避免大量结果占用上下文"
    }
  },
  "additionalProperties": false
}
""",
                                null,
                                READ,
                                (ctx, a) -> catalog.searchCatalog(ProductSearchRequest.from(a))),
                        tool(
                                "product_detail",
                                "按商品 ID 查询详情、全部 SKU、价格和库存。productId 必须来自商品查询结果，不能填 SKU ID。",
                                requiredSchema("productId", "integer"),
                                null,
                                READ,
                                (ctx, a) -> catalog.product(number(a, "productId"))));
    }

    /** 返回本功能已经声明的工具列表，由 ToolRegistry 统一注册。 */
    @Override
    public List<RegisteredTool> tools() {
        return tools;
    }
}
