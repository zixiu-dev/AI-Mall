-- 购物车与优惠券｜当前 MySQL 结构（V23）
-- 由 scripts/export-database-schema.py 导出。按文件编号顺序导入空库可校验结构。
-- 应用初始化由 Flyway 执行，快照仅含结构、不含业务数据或 Flyway 历史。
SET NAMES utf8mb4;

CREATE TABLE `cart_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '购物车项编号',
  `user_id` bigint NOT NULL COMMENT '购物车所属用户编号',
  `sku_id` bigint NOT NULL COMMENT '加入购物车的 SKU 编号',
  `quantity` int NOT NULL COMMENT '购物车中的购买数量',
  `selected` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否勾选参与购物车结算',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '首次加入购物车的时间',
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近修改购物车项的时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `user_id` (`user_id`,`sku_id`),
  KEY `fk_cart_item_sku_id` (`sku_id`),
  KEY `idx_cart_item_user` (`user_id`,`selected`),
  CONSTRAINT `fk_cart_item_sku_id` FOREIGN KEY (`sku_id`) REFERENCES `product_sku` (`id`),
  CONSTRAINT `fk_cart_item_user_id` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='购物车中的用户 SKU、数量和勾选状态';

CREATE TABLE `coupon` (
  `id` bigint NOT NULL COMMENT '优惠券模板编号',
  `name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '优惠券展示名称',
  `threshold_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '核销所需的最低商品金额，单位元',
  `discount_amount` decimal(12,2) NOT NULL COMMENT '模板面额，单位元',
  `total_stock` int NOT NULL COMMENT '模板可领取份数上限',
  `claimed_count` int NOT NULL DEFAULT '0' COMMENT '模板已领取份数',
  `start_at` datetime(6) NOT NULL COMMENT '生效时间',
  `end_at` datetime(6) NOT NULL COMMENT '失效时间',
  `status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'ACTIVE' COMMENT '模板启用状态，ACTIVE 表示启用',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='优惠券模板，定义领取额度、有效期和满减规则';

CREATE TABLE `user_coupon` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户券编号，建单选券使用此编号',
  `user_id` bigint NOT NULL COMMENT '领券用户编号',
  `coupon_id` bigint NOT NULL COMMENT '来源优惠券模板编号',
  `status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'AVAILABLE' COMMENT '用户券状态，如 AVAILABLE 可用、LOCKED 已占用、USED 已核销',
  `claimed_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '用户领取时间',
  `used_order_id` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '占用或使用这张券的订单编号',
  `used_at` datetime(6) DEFAULT NULL COMMENT '支付时核销优惠券的时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `user_id` (`user_id`,`coupon_id`),
  KEY `fk_user_coupon_coupon_id` (`coupon_id`),
  KEY `idx_user_coupon_user_status` (`user_id`,`status`),
  CONSTRAINT `fk_user_coupon_coupon_id` FOREIGN KEY (`coupon_id`) REFERENCES `coupon` (`id`),
  CONSTRAINT `fk_user_coupon_user_id` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='用户已领的具体优惠券，记录占用与核销';
