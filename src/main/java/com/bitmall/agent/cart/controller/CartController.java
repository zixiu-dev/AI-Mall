package com.bitmall.agent.cart.controller;

import com.bitmall.agent.cart.dto.CartAddRequest;
import com.bitmall.agent.cart.dto.CartUpdateRequest;
import com.bitmall.agent.cart.service.CartService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 手动购物车 HTTP 入口。 负责接收添加、修改、删除和查询请求，数量及库存等业务检查交给 CartService。 */
@RestController
@RequestMapping("/api/store")
@RequiredArgsConstructor
public class CartController {
    /** 校验并管理本人购物车条目，查询商品信息后组装购物车展示。 */
    private final CartService carts;

    /** 查询当前用户的购物车及勾选金额。 */
    @GetMapping("/cart")
    public Map<String, Object> cart(@RequestParam(defaultValue = "10001") long userId) {
        return carts.cart(userId);
    }

    /** 接收规格和数量，交给购物车服务校验库存后加入。 */
    @PostMapping("/cart")
    public Map<String, Object> addCart(@RequestBody CartAddRequest request) {
        return carts.addCartItem(
                request.resolvedUserId(), request.getSkuId(), request.resolvedQuantity());
    }

    /** 修改指定购物车项的数量或勾选状态。 */
    @PatchMapping("/cart/{itemId}")
    public Map<String, Object> updateCart(
            @PathVariable long itemId, @RequestBody CartUpdateRequest request) {
        return carts.updateCartItem(
                request.resolvedUserId(),
                itemId,
                request.resolvedQuantity(),
                request.getSelected());
    }

    /** 删除当前用户的指定购物车项，不允许删除其他用户的数据。 */
    @DeleteMapping("/cart/{itemId}")
    public Map<String, Object> removeCart(
            @PathVariable long itemId, @RequestParam(defaultValue = "10001") long userId) {
        return carts.removeCartItem(userId, itemId);
    }
}
