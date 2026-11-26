-- 新建库的最终建表定义，包含中文注释、索引及约束。
-- 已有 Flyway 历史的库沿独立迁移历史执行升级。

-- 用户与地址

CREATE TABLE `app_user` (
  `id` bigint NOT NULL COMMENT '用户编号',
  `nickname` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '用户展示昵称',
  `phone` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '用户联系电话',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '用户记录创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='商城用户，作为订单、地址、优惠券与会话的归属主体';

CREATE TABLE `user_address` (
  `id` bigint NOT NULL COMMENT '地址编号',
  `user_id` bigint NOT NULL COMMENT '地址所属用户编号',
  `receiver` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '收货人姓名',
  `phone` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '收货联系电话',
  `province` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '省级行政区',
  `city` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '市级行政区',
  `district` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '区县',
  `detail` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '街道门牌等详细地址',
  `is_default` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否为用户默认收货地址',
  PRIMARY KEY (`id`),
  KEY `fk_user_address_user_id` (`user_id`),
  CONSTRAINT `fk_user_address_user_id` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='用户的收货地址，建单时将所选地址复制到订单快照';

-- 商品、SKU 与属性

CREATE TABLE `product` (
  `id` bigint NOT NULL COMMENT '商品编号',
  `name` varchar(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '商品展示名称',
  `category` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '商品类目',
  `brand` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '商品品牌',
  `summary` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '商品简短介绍',
  `image_key` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '商城图片资源键',
  `rating` decimal(2,1) NOT NULL COMMENT '商品展示评分',
  `sales_count` int NOT NULL DEFAULT '0' COMMENT '商品展示销量',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '商品是否在商城启用',
  PRIMARY KEY (`id`),
  KEY `idx_product_catalog_filter` (`category`,`brand`,`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='商品主档，保存同一商品各规格共享的展示信息';

CREATE TABLE `product_attribute` (
  `product_id` bigint NOT NULL COMMENT '属性所属商品编号',
  `attr_key` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '属性键，如材质、用途或来源链接键',
  `attr_value` varchar(240) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '属性值',
  PRIMARY KEY (`product_id`,`attr_key`),
  KEY `idx_product_attribute_filter` (`attr_key`,`attr_value`,`product_id`),
  CONSTRAINT `fk_product_attribute_product_id` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='商品级扩展属性，可用于展示或检索';

CREATE TABLE `product_sku` (
  `id` bigint NOT NULL COMMENT 'SKU 编号，结算与建单按此定位规格',
  `product_id` bigint NOT NULL COMMENT 'SKU 所属商品编号',
  `sku_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '唯一业务规格编码',
  `spec` varchar(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '规格展示名称',
  `price` decimal(12,2) NOT NULL COMMENT '当前销售单价，单位元',
  `original_price` decimal(12,2) DEFAULT NULL COMMENT '展示用原价，单位元，未设置时为空',
  `stock` int NOT NULL COMMENT 'MySQL 可售库存，建单时扣减',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '此 SKU 是否启用',
  `locked_stock` int NOT NULL DEFAULT '0' COMMENT '已建单未支付的占用量，支付或关闭时扣减',
  `redis_inventory_initialized` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否已初始化 Redis 共享预扣池，用于识别库存池丢失',
  PRIMARY KEY (`id`),
  UNIQUE KEY `sku_code` (`sku_code`),
  KEY `idx_sku_catalog_filter` (`product_id`,`enabled`,`price`),
  CONSTRAINT `fk_product_sku_product_id` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`),
  CONSTRAINT `chk_sku_nonnegative_stock` CHECK (((`stock` >= 0) and (`locked_stock` >= 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='可交易的商品规格，保存规格级价格和 MySQL 库存';

CREATE TABLE `sku_attribute` (
  `sku_id` bigint NOT NULL COMMENT '属性所属 SKU 编号',
  `attr_key` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '规格属性键，如颜色或尺码',
  `attr_value` varchar(240) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '规格属性值',
  PRIMARY KEY (`sku_id`,`attr_key`),
  KEY `idx_sku_attribute_filter` (`attr_key`,`attr_value`,`sku_id`),
  CONSTRAINT `fk_sku_attribute_sku_id` FOREIGN KEY (`sku_id`) REFERENCES `product_sku` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='规格级扩展属性，用于颜色、尺码等规格筛选';

-- 购物车与优惠券

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

-- 下单、订单与库存

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

-- 订单消息

CREATE TABLE `order_message_outbox` (
  `event_id` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '消息事件编号，发送到 Broker 时作为消息键',
  `order_id` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '关联订单编号，未建单的命令允许为空',
  `topic` varchar(127) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'RocketMQ 目标主题',
  `event_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '消息类型，同时用于 RocketMQ 标签',
  `payload` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '待发送的业务消息正文 JSON',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'PENDING' COMMENT '发送状态，PENDING、SENDING、SENT 或 CANCELLED',
  `attempts` int NOT NULL DEFAULT '0' COMMENT '已领取发送的尝试次数',
  `next_attempt_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '下次允许尝试发送的时间',
  `lease_token` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '当前发送任务的租约令牌',
  `locked_until` datetime(6) DEFAULT NULL COMMENT '当前发送租约的到期时间',
  `broker_msg_id` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'Broker 确认的 MQ 消息编号',
  `last_error` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '最近一次发送或处理失败摘要',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Outbox 记录创建时间',
  `sent_at` datetime(6) DEFAULT NULL COMMENT '持久化发送成功状态的时间',
  `trace_id` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '关联购买意图编号，同一 trace 的同类事件唯一',
  `deliver_at_epoch_ms` bigint DEFAULT NULL COMMENT '定时消息的 Unix 毫秒投递时间，立即投递时为空',
  PRIMARY KEY (`event_id`),
  UNIQUE KEY `uk_order_message_trace_type` (`trace_id`,`event_type`),
  KEY `idx_order_message_pending` (`status`,`next_attempt_at`,`locked_until`),
  CONSTRAINT `fk_order_message_outbox_trace_id` FOREIGN KEY (`trace_id`) REFERENCES `order_submit_trace` (`trace_id`),
  CONSTRAINT `chk_order_message_status` CHECK ((`status` in (_utf8mb4'PENDING',_utf8mb4'SENDING',_utf8mb4'SENT',_utf8mb4'CANCELLED'))),
  CONSTRAINT `chk_outbox_delivery_time` CHECK (((`deliver_at_epoch_ms` is null) or (`deliver_at_epoch_ms` > 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='订单 Outbox，持久化建单命令和生命周期消息及发送进度';

CREATE TABLE `order_event_receipt` (
  `event_id` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '已接收的消息事件编号',
  `order_id` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '事件指向的订单编号',
  `event_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '订单生命周期事件类型',
  `user_id` bigint NOT NULL COMMENT '事件携带的订单所属用户编号',
  `pay_amount` decimal(12,2) NOT NULL COMMENT '事件携带的订单金额，单位元',
  `occurred_at` datetime(6) NOT NULL COMMENT '事件在生产方发生的时间',
  `received_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '消费回执落库时间',
  PRIMARY KEY (`event_id`),
  KEY `idx_order_event_receipt_order` (`order_id`,`received_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='订单生命周期消息的消费回执，按事件编号去重和查阅';

-- 物流与售后

CREATE TABLE `logistics_event` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物流事件编号',
  `order_id` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '物流事件所属订单编号',
  `event_time` datetime(6) NOT NULL COMMENT '物流节点发生时间',
  `status` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '物流事件状态',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '物流节点的展示描述',
  PRIMARY KEY (`id`),
  KEY `fk_logistics_event_order_id` (`order_id`),
  CONSTRAINT `fk_logistics_event_order_id` FOREIGN KEY (`order_id`) REFERENCES `customer_order` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='按订单记录物流时间线事件';

CREATE TABLE `after_sales` (
  `id` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '售后申请编号',
  `order_id` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '申请对应的订单编号',
  `user_id` bigint NOT NULL COMMENT '发起售后的用户编号',
  `type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '售后申请类型',
  `reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '用户填写的售后原因',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '售后申请处理状态',
  `refund_amount` decimal(12,2) DEFAULT NULL COMMENT '申请记录中的退款金额，单位元，可为空',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '售后申请创建时间',
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近处理更新时间',
  PRIMARY KEY (`id`),
  KEY `fk_after_sales_order_id` (`order_id`),
  KEY `fk_after_sales_user_id` (`user_id`),
  CONSTRAINT `fk_after_sales_order_id` FOREIGN KEY (`order_id`) REFERENCES `customer_order` (`id`),
  CONSTRAINT `fk_after_sales_user_id` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='用户提交的售后申请及当前处理进度';

-- Agent 会话、技能与监控

CREATE TABLE `conversation` (
  `id` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '会话编号，对应监控页的 Thread ID',
  `user_id` bigint NOT NULL COMMENT '会话所属用户编号',
  `title` varchar(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '新会话' COMMENT '会话展示标题',
  `summary` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin COMMENT '较早轮次的滚动摘要',
  `summarized_until_id` bigint DEFAULT NULL COMMENT '已包含在摘要中的最后一条消息编号',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '会话创建时间',
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新或新增消息的时间',
  PRIMARY KEY (`id`),
  KEY `fk_conversation_user_id` (`user_id`),
  CONSTRAINT `fk_conversation_user_id` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='Agent 会话主表，一个会话可包含多轮问答';

CREATE TABLE `chat_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '消息编号，后台助手回复的编号也是轮次编号',
  `conversation_id` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '消息所属会话编号',
  `role` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '消息的发言角色',
  `content` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '页面消息正文，后台回答生成时可以为空',
  `metadata` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT (_utf8mb4'{}') COMMENT '消息附加信息 JSON',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '消息写入时间，后台回复为占位创建时间',
  `generation_status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '后台回答生成状态，普通消息为空',
  `reply_to_message_id` bigint DEFAULT NULL COMMENT '本次助手回复对应的用户消息编号',
  `request_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '去重键，由用户编号与客户端本次提交编号组成',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_chat_message_request_key` (`request_key`),
  KEY `idx_chat_message_conversation` (`conversation_id`,`id`),
  KEY `idx_chat_message_generation` (`generation_status`,`conversation_id`,`id`),
  CONSTRAINT `fk_chat_message_conversation_id` FOREIGN KEY (`conversation_id`) REFERENCES `conversation` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='Agent 的用户与助手消息，后台回答复用助手消息编号作为 Turn ID';

CREATE TABLE `agent_scope_state` (
  `user_id` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'Agent 运行状态所属用户的字符串标识',
  `session_id` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'Agent 运行状态所属会话标识',
  `state_key` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '运行状态键，当前使用 spring-ai-v1 存放模型消息',
  `payload` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '序列化的模型上下文及工具消息',
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '运行状态最近写入时间',
  PRIMARY KEY (`user_id`,`session_id`,`state_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='Agent 模型运行状态，当前 Spring AI 消息沿用此历史表名';

CREATE TABLE `user_memory` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户偏好记录编号',
  `user_id` bigint NOT NULL COMMENT '偏好所属用户编号',
  `memory_key` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '偏好键，同一用户下唯一',
  `memory_value` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '用户授权记录的偏好内容',
  `source_conversation_id` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '形成这条偏好的来源会话编号',
  `version` int NOT NULL DEFAULT '0' COMMENT '偏好记录版本',
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '偏好最近更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `user_id` (`user_id`,`memory_key`),
  CONSTRAINT `fk_user_memory_user_id` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='用户明确要求长期保留的购物偏好';

CREATE TABLE `agent_skill` (
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '技能的稳定标识，用于 load_skill 和校验',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '技能展示名称',
  `description` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '何时适合加载此技能的简短说明',
  `manual` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '技能办理手册正文',
  `tool_names` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '技能对应的工具名称列表 JSON',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '技能是否启用',
  PRIMARY KEY (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='Agent 可按需加载的业务技能手册和关联工具';

CREATE TABLE `conversation_skill` (
  `conversation_id` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '加载技能的会话编号',
  `skill_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '被加载的技能编码',
  `loaded_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '技能加载记录的写入时间',
  PRIMARY KEY (`conversation_id`,`skill_code`),
  KEY `fk_conversation_skill_skill_code` (`skill_code`),
  CONSTRAINT `fk_conversation_skill_conversation_id` FOREIGN KEY (`conversation_id`) REFERENCES `conversation` (`id`),
  CONSTRAINT `fk_conversation_skill_skill_code` FOREIGN KEY (`skill_code`) REFERENCES `agent_skill` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='会话与已加载 Agent 技能的关联';

CREATE TABLE `pending_action` (
  `id` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '待确认操作编号',
  `idempotency_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '创建待确认操作时使用的幂等键',
  `user_id` bigint NOT NULL COMMENT '有权确认的用户编号',
  `conversation_id` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '创建该操作的会话编号',
  `tool_name` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '待确认的写工具名称',
  `arguments` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '待确认操作的规范化参数 JSON',
  `arguments_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '参数摘要，用于识别操作参数',
  `summary` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '确认卡向用户展示的操作摘要',
  `status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '待确认操作的处理状态',
  `result` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin COMMENT '确认执行或失败后的结果 JSON',
  `version` int NOT NULL DEFAULT '0' COMMENT '用于确认状态更新的并发版本号',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '确认操作的创建时间',
  `decided_at` datetime(6) DEFAULT NULL COMMENT '用户作出确认或拒绝决定的时间',
  `executed_at` datetime(6) DEFAULT NULL COMMENT '记录实际执行结果的时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idempotency_key` (`idempotency_key`),
  KEY `fk_pending_action_user_id` (`user_id`),
  KEY `idx_pending_action_conversation` (`conversation_id`,`status`,`created_at`),
  CONSTRAINT `fk_pending_action_conversation_id` FOREIGN KEY (`conversation_id`) REFERENCES `conversation` (`id`),
  CONSTRAINT `fk_pending_action_user_id` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='Agent 待用户确认的操作，保存可信参数及确认执行结果';

CREATE TABLE `agent_model_call` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '模型调用明细主键',
  `turn_id` bigint NOT NULL COMMENT '后台助手消息编号，即所在轮次',
  `sequence_no` int NOT NULL COMMENT '同轮事件序号，与执行项共用排序口径',
  `step_no` int NOT NULL COMMENT 'Agent 循环中的推理步骤编号',
  `attempt_no` int NOT NULL COMMENT '同一步内实际模型请求的尝试编号',
  `candidate` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '模型候选角色，如 primary 或 bailian',
  `model` varchar(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '实际请求的模型标识',
  `status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '此次模型请求的结果状态',
  `error_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '安全归类后的模型失败代码',
  `started_at` datetime(6) NOT NULL COMMENT '调用开始时间，按 UTC 记录',
  `duration_ms` bigint NOT NULL COMMENT '调用总耗时，单位毫秒',
  `first_text_ms` bigint DEFAULT NULL COMMENT '首段文本耗时，未收到文字时为空',
  `input_tokens` int DEFAULT NULL COMMENT '上游回传的输入 Token，未回传时为空',
  `output_tokens` int DEFAULT NULL COMMENT '上游回传的输出 Token，未回传时为空',
  PRIMARY KEY (`id`),
  UNIQUE KEY `turn_id` (`turn_id`,`sequence_no`),
  UNIQUE KEY `turn_id_2` (`turn_id`,`step_no`,`attempt_no`),
  KEY `idx_agent_model_call_retention` (`started_at`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='Agent 每次真实模型请求的诊断明细';

CREATE TABLE `agent_turn_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '执行项明细主键',
  `turn_id` bigint NOT NULL COMMENT '后台助手消息编号，即所在轮次',
  `sequence_no` int NOT NULL COMMENT '同轮事件序号，与模型请求共用排序口径',
  `step_no` int DEFAULT NULL COMMENT '所属推理步骤，轮次级事件可为空',
  `item_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '执行项类型，如 QUEUE_WAIT 或 TOOL_CALL',
  `model_call_sequence` int DEFAULT NULL COMMENT '触发该工具的模型事件序号，仅在同轮内引用',
  `candidate` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '被熔断跳过的候选模型角色',
  `tool_call_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '上游模型的工具请求标识',
  `tool_name` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '被请求的已注册工具名称',
  `execution_count` int DEFAULT NULL COMMENT '实际分发次数，校验拒绝为零，实际重试累加',
  `action_id` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '生成或恢复的确认卡操作编号',
  `status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '此执行项的结果状态',
  `error_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '安全归类后的失败代码',
  `started_at` datetime(6) NOT NULL COMMENT '事件开始时间，按 UTC 记录',
  `duration_ms` bigint NOT NULL COMMENT '事件持续耗时，单位毫秒',
  PRIMARY KEY (`id`),
  UNIQUE KEY `turn_id` (`turn_id`,`sequence_no`),
  KEY `idx_agent_turn_item_retention` (`started_at`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='Agent 一轮的排队、工具、熔断跳过及确认卡恢复等诊断事件';

-- RAG 源文档与兼容索引

CREATE TABLE `knowledge_document` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '源文档内部编号',
  `category` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '文档的可读业务分类',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '政策文档标题',
  `keywords` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '关键词检索使用的预置词',
  `content` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '政策正文',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '文档是否可参与当前检索或索引构建',
  `brand_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'PLATFORM' COMMENT '适用品牌编码，PLATFORM 代表商城自身政策',
  `document_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'GENERAL' COMMENT '政策类型，如退换货、退款到账或保修',
  `document_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '用于索引及更新的稳定文档标识',
  `source_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '政策来源链接',
  `source_name` varchar(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '政策发布方或出处名称',
  `source_checked_at` date DEFAULT NULL COMMENT '最近核对来源的日期',
  `applicable_channel` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '文档适用的销售或服务渠道描述',
  `region` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'CN_MAINLAND' COMMENT '政策适用地区编码',
  `content_version` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '当前正文内容的版本',
  `scope_note` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '适用范围与例外补充说明',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_knowledge_document_key` (`document_key`),
  KEY `idx_knowledge_brand_type` (`enabled`,`brand_code`,`document_type`,`region`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='RAG 源文档，保存政策正文、来源和适用范围';

CREATE TABLE `knowledge_index_version` (
  `index_version` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '单次索引构建的版本标识',
  `build_status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '构建状态，如 BUILDING、PUBLISHED 或 FAILED',
  `embedding_model_id` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '生成此索引的向量模型标识',
  `embedding_version` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '向量配置的版本标识',
  `dimension` int NOT NULL COMMENT '向量维度',
  `document_count` int NOT NULL COMMENT '此索引覆盖的源文档数量',
  `chunk_count` int NOT NULL COMMENT '此索引包含的文本切片数量',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '索引构建记录创建时间',
  `published_at` timestamp NULL DEFAULT NULL COMMENT '此版本发布的时间',
  PRIMARY KEY (`index_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='MySQL LEGACY 兼容索引的构建与发布版本';

CREATE TABLE `knowledge_index_head` (
  `head_id` int NOT NULL COMMENT '发布指针固定记录键',
  `index_version` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '当前可用的索引版本，尚未发布时为空',
  PRIMARY KEY (`head_id`),
  KEY `index_version` (`index_version`),
  CONSTRAINT `knowledge_index_head_ibfk_1` FOREIGN KEY (`index_version`) REFERENCES `knowledge_index_version` (`index_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='MySQL LEGACY 兼容索引当前发布版本的指针';

CREATE TABLE `knowledge_index_chunk` (
  `index_version` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '切片归属的索引版本',
  `chunk_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '索引版本内的切片标识',
  `document_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '来源文档的稳定标识',
  `content_version` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '源文档正文版本',
  `document_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '切分时的源文档摘要',
  `text_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '当前切片正文摘要',
  `section_title` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '切片所在文档小节标题',
  `chunk_text` text CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '供检索和引用的切片正文',
  `vector_json` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '兼容后端使用的序列化向量 JSON',
  `metadata_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '构建时记录的来源、渠道等引用属性 JSON',
  `brand_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '供检索过滤的品牌编码',
  `document_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '供检索过滤的政策类型',
  `region` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '供检索过滤的地区编码',
  PRIMARY KEY (`index_version`,`chunk_id`),
  KEY `idx_knowledge_chunk_scope` (`index_version`,`brand_code`,`document_type`,`region`),
  CONSTRAINT `knowledge_index_chunk_ibfk_1` FOREIGN KEY (`index_version`) REFERENCES `knowledge_index_version` (`index_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='MySQL LEGACY 兼容索引切片，用 JSON 保存向量与来源元数据';
