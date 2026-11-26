-- 下单、订单与库存｜当前 MySQL 结构（V23）
-- 由 scripts/export-database-schema.py 导出。按文件编号顺序导入空库可校验结构。
-- 应用初始化由 Flyway 执行，快照仅含结构、不含业务数据或 Flyway 历史。
SET NAMES utf8mb4;

CREATE TABLE `order_submit_trace` (
  `trace_id` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '购买意图编号，前端按此查询建单结果',
  `user_id` bigint NOT NULL COMMENT '发起购买的用户编号',
  `origin_key` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '购买来源唯一键，如 Agent 确认操作关联键',
  `mode` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '实际建单模式，SYNC 同步或 ASYNC 异步',
  `request_snapshot` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '持久化的购买与结算快照 JSON，供后续建单和恢复使用',
  `request_hash` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '购买快照摘要，用于核对原始意图',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'INIT' COMMENT '购买状态，INIT、PROCESSING、SUCCESS、FAILED 或 EXPIRED',
  `reserve_status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'NONE' COMMENT 'Redis 库存预占及后续确认、释放、返还的进度',
  `order_id` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '成功创建的订单编号',
  `result_json` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin COMMENT '已保存的建单结果 JSON，重复查询可复用',
  `failure_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '购买失败的业务错误码',
  `failure_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '可展示的购买失败原因',
  `version` int NOT NULL DEFAULT '0' COMMENT '用于状态条件更新的并发版本号',
  `expires_at` datetime(6) NOT NULL COMMENT '购买意图在受理前的有效期截止时间',
  `next_check_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '后台下次检查或恢复此购买的最早时间',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '购买意图创建时间',
  `accepted_at` datetime(6) DEFAULT NULL COMMENT '可靠受理购买的时间，可早于真实建单',
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近状态更新时间',
  `finished_at` datetime(6) DEFAULT NULL COMMENT '写入购买终态的时间',
  `processing_expires_at` datetime(6) DEFAULT NULL COMMENT '异步受理后实际建单的处理截止时间',
  `submission_hash` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '首次提交参数的摘要，用于核对同一购买编号的重试',
  PRIMARY KEY (`trace_id`),
  UNIQUE KEY `origin_key` (`origin_key`),
  KEY `idx_order_trace_recovery` (`status`,`reserve_status`,`next_check_at`),
  KEY `idx_order_trace_user` (`user_id`,`created_at`),
  KEY `idx_order_trace_pending` (`status`,`mode`,`next_check_at`,`processing_expires_at`),
  CONSTRAINT `fk_order_submit_trace_user_id` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`),
  CONSTRAINT `order_submit_trace_chk_1` CHECK ((`mode` in (_utf8mb4'SYNC',_utf8mb4'ASYNC'))),
  CONSTRAINT `order_submit_trace_chk_2` CHECK ((`status` in (_utf8mb4'INIT',_utf8mb4'PROCESSING',_utf8mb4'SUCCESS',_utf8mb4'FAILED',_utf8mb4'EXPIRED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='一次购买意图的持久化记录，保存幂等关联、购买结果和库存整理进度';

CREATE TABLE `customer_order` (
  `id` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '订单编号',
  `user_id` bigint NOT NULL COMMENT '下单用户编号',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '订单生命周期状态，如 PENDING_PAYMENT、PAID、CANCELLED',
  `goods_amount` decimal(12,2) NOT NULL COMMENT '优惠前商品总金额，单位元',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '本订单实际抵扣金额，单位元',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '订单运费，单位元',
  `pay_amount` decimal(12,2) NOT NULL COMMENT '最终应付金额，单位元',
  `address_snapshot` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '下单时的收货地址快照',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '成功建单时间',
  `paid_at` datetime(6) DEFAULT NULL COMMENT '支付成功时间，未支付时为空',
  `cancelled_at` datetime(6) DEFAULT NULL COMMENT '订单被取消或超时关闭的时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '用于订单并发状态更新的版本号',
  `coupon_id` bigint DEFAULT NULL COMMENT '下单使用的优惠券模板编号，未用券时为空',
  `coupon_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '下单时的优惠券名称快照',
  `submit_trace_id` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '来源购买意图编号，唯一约束保证同一 trace 至多建一单',
  `payment_expires_at` datetime(6) DEFAULT NULL COMMENT '自建单起计算的支付截止时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_customer_order_trace` (`submit_trace_id`),
  KEY `fk_customer_order_user_id` (`user_id`),
  KEY `idx_order_payment_timeout` (`status`,`payment_expires_at`),
  CONSTRAINT `fk_customer_order_submit_trace_id` FOREIGN KEY (`submit_trace_id`) REFERENCES `order_submit_trace` (`trace_id`),
  CONSTRAINT `fk_customer_order_user_id` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='订单主表，保存订单金额、状态、收货和支付期限快照';

CREATE TABLE `order_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '订单商品行编号',
  `order_id` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '所属订单编号',
  `product_id` bigint NOT NULL COMMENT '成交商品编号',
  `sku_id` bigint NOT NULL COMMENT '成交 SKU 编号',
  `product_name` varchar(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '成交时的商品名称快照',
  `sku_spec` varchar(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '成交时的规格名称快照',
  `unit_price` decimal(12,2) NOT NULL COMMENT '成交时的商品单价，单位元',
  `quantity` int NOT NULL COMMENT '此订单行的购买件数',
  PRIMARY KEY (`id`),
  KEY `fk_order_item_order_id` (`order_id`),
  KEY `fk_order_item_product_id` (`product_id`),
  KEY `fk_order_item_sku_id` (`sku_id`),
  CONSTRAINT `fk_order_item_order_id` FOREIGN KEY (`order_id`) REFERENCES `customer_order` (`id`),
  CONSTRAINT `fk_order_item_product_id` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`),
  CONSTRAINT `fk_order_item_sku_id` FOREIGN KEY (`sku_id`) REFERENCES `product_sku` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='订单商品行，保留成交时的商品、规格、单价和数量';

CREATE TABLE `order_inventory_return` (
  `order_id` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '已关闭订单编号',
  `sku_id` bigint NOT NULL COMMENT '待返还的 SKU 编号',
  `reservation_id` varchar(90) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '库存预占标识，通常是购买 trace，旧单可用派生标识',
  `quantity` int NOT NULL COMMENT '待返还到 Redis 可售池的件数',
  `completed` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Redis 返还任务是否完成',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '订单关闭事务创建返还任务的时间',
  PRIMARY KEY (`order_id`,`sku_id`),
  KEY `fk_inventory_return_sku` (`sku_id`),
  KEY `idx_inventory_return_pending` (`completed`,`created_at`),
  CONSTRAINT `fk_inventory_return_order` FOREIGN KEY (`order_id`) REFERENCES `customer_order` (`id`),
  CONSTRAINT `fk_inventory_return_sku` FOREIGN KEY (`sku_id`) REFERENCES `product_sku` (`id`),
  CONSTRAINT `order_inventory_return_chk_1` CHECK ((`quantity` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='关闭未支付订单后的 Redis 库存返还任务';
