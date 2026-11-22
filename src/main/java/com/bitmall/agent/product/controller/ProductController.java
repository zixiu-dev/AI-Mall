package com.bitmall.agent.product.controller;

import com.bitmall.agent.product.service.ProductService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import com.bitmall.agent.product.dto.ProductSearchRequest;

/** 商品查询 HTTP 入口。 接收搜索条件、筛选选项和商品编号，统一调用 ProductService，让页面与助手使用相同商品数据和缓存。 */
@RestController
@RequestMapping("/api/store")
@RequiredArgsConstructor
public class ProductController {
    /** 商品搜索、搜索筛选项和商品详情。 */
    private final ProductService catalog;

    /** 把 URL 中的预算、颜色、尺码等参数组装为搜索请求，再走统一商品查询。 */
    @GetMapping("/products")
    public Object products(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String color,
            @RequestParam(required = false) String size,
            @RequestParam(required = false) String material,
            @RequestParam(required = false) String useCase,
            @RequestParam(defaultValue = "false") boolean inStock,
            @RequestParam(defaultValue = "recommended") String sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return catalog.searchCatalog(
                new ProductSearchRequest(
                        query, category, brand, minPrice, maxPrice, color, size, material, useCase,
                        inStock, sort, page, limit));
    }

    /** 查询指定品类可用的品牌、颜色、尺码等选项。 */
    @GetMapping("/product-filters")
    public Object filters(@RequestParam(required = false) String category) {
        return catalog.searchOptions(category);
    }

    /** 按商品编号查询完整详情和各个可见规格。 */
    @GetMapping("/products/{productId}")
    public Object product(@PathVariable long productId) {
        return catalog.product(productId);
    }
}
