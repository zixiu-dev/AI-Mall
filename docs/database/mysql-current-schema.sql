-- 当前 MySQL 表结构快照；由 scripts/export-database-schema.py 从空库重放 V1～V20 后导出。
-- 阅读/对照用途，含表与索引，不含业务数据、Flyway 历史、密钥或旧演化语句。
-- 正常初始化请启动应用交给 Flyway；不要先导入本文件再让 Flyway 执行 V1。
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS=0;

-- after_sales
CREATE TABLE `after_sales` (
  `id` varchar(40) COLLATE utf8mb4_bin NOT NULL,
  `order_id` varchar(40) COLLATE utf8mb4_bin NOT NULL,
  `user_id` bigint NOT NULL,
  `type` varchar(32) COLLATE utf8mb4_bin NOT NULL,
  `reason` varchar(500) COLLATE utf8mb4_bin NOT NULL,
  `status` varchar(32) COLLATE utf8mb4_bin NOT NULL,
  `refund_amount` decimal(12,2) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `fk_after_sales_order_id` (`order_id`),
  KEY `fk_after_sales_user_id` (`user_id`),
  CONSTRAINT `fk_after_sales_order_id` FOREIGN KEY (`order_id`) REFERENCES `customer_order` (`id`),
  CONSTRAINT `fk_after_sales_user_id` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- agent_scope_state
CREATE TABLE `agent_scope_state` (
  `user_id` varchar(80) COLLATE utf8mb4_bin NOT NULL,
  `session_id` varchar(80) COLLATE utf8mb4_bin NOT NULL,
  `state_key` varchar(120) COLLATE utf8mb4_bin NOT NULL,
  `payload` mediumtext COLLATE utf8mb4_bin NOT NULL,
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`user_id`,`session_id`,`state_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- agent_skill
CREATE TABLE `agent_skill` (
  `code` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `name` varchar(100) COLLATE utf8mb4_bin NOT NULL,
  `description` varchar(300) COLLATE utf8mb4_bin NOT NULL,
  `manual` mediumtext COLLATE utf8mb4_bin NOT NULL,
  `tool_names` mediumtext COLLATE utf8mb4_bin NOT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT '1',
  PRIMARY KEY (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- app_user
CREATE TABLE `app_user` (
  `id` bigint NOT NULL,
  `nickname` varchar(80) COLLATE utf8mb4_bin NOT NULL,
  `phone` varchar(32) COLLATE utf8mb4_bin NOT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- cart_item
CREATE TABLE `cart_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `sku_id` bigint NOT NULL,
  `quantity` int NOT NULL,
  `selected` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `user_id` (`user_id`,`sku_id`),
  KEY `fk_cart_item_sku_id` (`sku_id`),
  KEY `idx_cart_item_user` (`user_id`,`selected`),
  CONSTRAINT `fk_cart_item_sku_id` FOREIGN KEY (`sku_id`) REFERENCES `product_sku` (`id`),
  CONSTRAINT `fk_cart_item_user_id` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- chat_message
CREATE TABLE `chat_message` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `conversation_id` varchar(40) COLLATE utf8mb4_bin NOT NULL,
  `role` varchar(20) COLLATE utf8mb4_bin NOT NULL,
  `content` mediumtext COLLATE utf8mb4_bin NOT NULL,
  `metadata` mediumtext COLLATE utf8mb4_bin NOT NULL DEFAULT (_utf8mb4'{}'),
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `generation_status` varchar(16) COLLATE utf8mb4_bin DEFAULT NULL,
  `reply_to_message_id` bigint DEFAULT NULL,
  `request_key` varchar(100) COLLATE utf8mb4_bin DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_chat_message_request_key` (`request_key`),
  KEY `idx_chat_message_conversation` (`conversation_id`,`id`),
  KEY `idx_chat_message_generation` (`generation_status`,`conversation_id`,`id`),
  CONSTRAINT `fk_chat_message_conversation_id` FOREIGN KEY (`conversation_id`) REFERENCES `conversation` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- conversation
CREATE TABLE `conversation` (
  `id` varchar(40) COLLATE utf8mb4_bin NOT NULL,
  `user_id` bigint NOT NULL,
  `title` varchar(160) COLLATE utf8mb4_bin NOT NULL DEFAULT '新会话',
  `summary` mediumtext COLLATE utf8mb4_bin,
  `summarized_until_id` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `fk_conversation_user_id` (`user_id`),
  CONSTRAINT `fk_conversation_user_id` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- conversation_skill
CREATE TABLE `conversation_skill` (
  `conversation_id` varchar(40) COLLATE utf8mb4_bin NOT NULL,
  `skill_code` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `loaded_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`conversation_id`,`skill_code`),
  KEY `fk_conversation_skill_skill_code` (`skill_code`),
  CONSTRAINT `fk_conversation_skill_conversation_id` FOREIGN KEY (`conversation_id`) REFERENCES `conversation` (`id`),
  CONSTRAINT `fk_conversation_skill_skill_code` FOREIGN KEY (`skill_code`) REFERENCES `agent_skill` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- coupon
CREATE TABLE `coupon` (
  `id` bigint NOT NULL,
  `name` varchar(120) COLLATE utf8mb4_bin NOT NULL,
  `threshold_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(12,2) NOT NULL,
  `total_stock` int NOT NULL,
  `claimed_count` int NOT NULL DEFAULT '0',
  `start_at` datetime(6) NOT NULL,
  `end_at` datetime(6) NOT NULL,
  `status` varchar(24) COLLATE utf8mb4_bin NOT NULL DEFAULT 'ACTIVE',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- customer_order
CREATE TABLE `customer_order` (
  `id` varchar(40) COLLATE utf8mb4_bin NOT NULL,
  `user_id` bigint NOT NULL,
  `status` varchar(32) COLLATE utf8mb4_bin NOT NULL,
  `goods_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `freight_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `pay_amount` decimal(12,2) NOT NULL,
  `address_snapshot` varchar(500) COLLATE utf8mb4_bin NOT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `paid_at` datetime(6) DEFAULT NULL,
  `cancelled_at` datetime(6) DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  `coupon_id` bigint DEFAULT NULL,
  `coupon_name` varchar(120) COLLATE utf8mb4_bin DEFAULT NULL,
  `submit_trace_id` varchar(40) COLLATE utf8mb4_bin DEFAULT NULL,
  `payment_expires_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_customer_order_trace` (`submit_trace_id`),
  KEY `fk_customer_order_user_id` (`user_id`),
  KEY `idx_order_payment_timeout` (`status`,`payment_expires_at`),
  CONSTRAINT `fk_customer_order_submit_trace_id` FOREIGN KEY (`submit_trace_id`) REFERENCES `order_submit_trace` (`trace_id`),
  CONSTRAINT `fk_customer_order_user_id` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- knowledge_document
CREATE TABLE `knowledge_document` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `category` varchar(60) COLLATE utf8mb4_bin NOT NULL,
  `title` varchar(200) COLLATE utf8mb4_bin NOT NULL,
  `keywords` varchar(300) COLLATE utf8mb4_bin NOT NULL,
  `content` mediumtext COLLATE utf8mb4_bin NOT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT '1',
  `brand_code` varchar(32) COLLATE utf8mb4_bin NOT NULL DEFAULT 'PLATFORM',
  `document_type` varchar(32) COLLATE utf8mb4_bin NOT NULL DEFAULT 'GENERAL',
  `document_key` varchar(100) COLLATE utf8mb4_bin DEFAULT NULL,
  `source_url` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL,
  `source_name` varchar(160) COLLATE utf8mb4_bin DEFAULT NULL,
  `source_checked_at` date DEFAULT NULL,
  `applicable_channel` varchar(300) COLLATE utf8mb4_bin DEFAULT NULL,
  `region` varchar(40) COLLATE utf8mb4_bin NOT NULL DEFAULT 'CN_MAINLAND',
  `content_version` varchar(80) COLLATE utf8mb4_bin DEFAULT NULL,
  `scope_note` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_knowledge_document_key` (`document_key`),
  KEY `idx_knowledge_brand_type` (`enabled`,`brand_code`,`document_type`,`region`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- knowledge_index_chunk
CREATE TABLE `knowledge_index_chunk` (
  `index_version` varchar(80) COLLATE utf8mb4_bin NOT NULL,
  `chunk_id` varchar(100) COLLATE utf8mb4_bin NOT NULL,
  `document_key` varchar(100) COLLATE utf8mb4_bin NOT NULL,
  `content_version` varchar(80) COLLATE utf8mb4_bin NOT NULL,
  `document_hash` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `text_hash` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `section_title` varchar(300) COLLATE utf8mb4_bin NOT NULL,
  `chunk_text` text COLLATE utf8mb4_bin NOT NULL,
  `vector_json` mediumtext COLLATE utf8mb4_bin NOT NULL,
  `metadata_json` text COLLATE utf8mb4_bin NOT NULL,
  `brand_code` varchar(32) COLLATE utf8mb4_bin NOT NULL,
  `document_type` varchar(32) COLLATE utf8mb4_bin NOT NULL,
  `region` varchar(40) COLLATE utf8mb4_bin NOT NULL,
  PRIMARY KEY (`index_version`,`chunk_id`),
  KEY `idx_knowledge_chunk_scope` (`index_version`,`brand_code`,`document_type`,`region`),
  CONSTRAINT `knowledge_index_chunk_ibfk_1` FOREIGN KEY (`index_version`) REFERENCES `knowledge_index_version` (`index_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- knowledge_index_head
CREATE TABLE `knowledge_index_head` (
  `head_id` int NOT NULL,
  `index_version` varchar(80) COLLATE utf8mb4_bin DEFAULT NULL,
  PRIMARY KEY (`head_id`),
  KEY `index_version` (`index_version`),
  CONSTRAINT `knowledge_index_head_ibfk_1` FOREIGN KEY (`index_version`) REFERENCES `knowledge_index_version` (`index_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- knowledge_index_version
CREATE TABLE `knowledge_index_version` (
  `index_version` varchar(80) COLLATE utf8mb4_bin NOT NULL,
  `build_status` varchar(20) COLLATE utf8mb4_bin NOT NULL,
  `embedding_model_id` varchar(200) COLLATE utf8mb4_bin NOT NULL,
  `embedding_version` varchar(120) COLLATE utf8mb4_bin NOT NULL,
  `dimension` int NOT NULL,
  `document_count` int NOT NULL,
  `chunk_count` int NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `published_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`index_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- logistics_event
CREATE TABLE `logistics_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` varchar(40) COLLATE utf8mb4_bin NOT NULL,
  `event_time` datetime(6) NOT NULL,
  `status` varchar(40) COLLATE utf8mb4_bin NOT NULL,
  `description` varchar(500) COLLATE utf8mb4_bin NOT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_logistics_event_order_id` (`order_id`),
  CONSTRAINT `fk_logistics_event_order_id` FOREIGN KEY (`order_id`) REFERENCES `customer_order` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- order_event_receipt
CREATE TABLE `order_event_receipt` (
  `event_id` varchar(40) COLLATE utf8mb4_bin NOT NULL,
  `order_id` varchar(40) COLLATE utf8mb4_bin NOT NULL,
  `event_type` varchar(32) COLLATE utf8mb4_bin NOT NULL,
  `user_id` bigint NOT NULL,
  `pay_amount` decimal(12,2) NOT NULL,
  `occurred_at` datetime(6) NOT NULL,
  `received_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`event_id`),
  KEY `idx_order_event_receipt_order` (`order_id`,`received_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- order_inventory_return
CREATE TABLE `order_inventory_return` (
  `order_id` varchar(40) COLLATE utf8mb4_bin NOT NULL,
  `sku_id` bigint NOT NULL,
  `reservation_id` varchar(90) COLLATE utf8mb4_bin NOT NULL,
  `quantity` int NOT NULL,
  `completed` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`order_id`,`sku_id`),
  KEY `fk_inventory_return_sku` (`sku_id`),
  KEY `idx_inventory_return_pending` (`completed`,`created_at`),
  CONSTRAINT `fk_inventory_return_order` FOREIGN KEY (`order_id`) REFERENCES `customer_order` (`id`),
  CONSTRAINT `fk_inventory_return_sku` FOREIGN KEY (`sku_id`) REFERENCES `product_sku` (`id`),
  CONSTRAINT `order_inventory_return_chk_1` CHECK ((`quantity` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- order_item
CREATE TABLE `order_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` varchar(40) COLLATE utf8mb4_bin NOT NULL,
  `product_id` bigint NOT NULL,
  `sku_id` bigint NOT NULL,
  `product_name` varchar(160) COLLATE utf8mb4_bin NOT NULL,
  `sku_spec` varchar(160) COLLATE utf8mb4_bin NOT NULL,
  `unit_price` decimal(12,2) NOT NULL,
  `quantity` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_order_item_order_id` (`order_id`),
  KEY `fk_order_item_product_id` (`product_id`),
  KEY `fk_order_item_sku_id` (`sku_id`),
  CONSTRAINT `fk_order_item_order_id` FOREIGN KEY (`order_id`) REFERENCES `customer_order` (`id`),
  CONSTRAINT `fk_order_item_product_id` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`),
  CONSTRAINT `fk_order_item_sku_id` FOREIGN KEY (`sku_id`) REFERENCES `product_sku` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- order_message_outbox
CREATE TABLE `order_message_outbox` (
  `event_id` varchar(40) COLLATE utf8mb4_bin NOT NULL,
  `order_id` varchar(40) COLLATE utf8mb4_bin DEFAULT NULL,
  `topic` varchar(127) COLLATE utf8mb4_bin NOT NULL,
  `event_type` varchar(32) COLLATE utf8mb4_bin NOT NULL,
  `payload` mediumtext COLLATE utf8mb4_bin NOT NULL,
  `status` varchar(16) COLLATE utf8mb4_bin NOT NULL DEFAULT 'PENDING',
  `attempts` int NOT NULL DEFAULT '0',
  `next_attempt_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `lease_token` varchar(40) COLLATE utf8mb4_bin DEFAULT NULL,
  `locked_until` datetime(6) DEFAULT NULL,
  `broker_msg_id` varchar(200) COLLATE utf8mb4_bin DEFAULT NULL,
  `last_error` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `sent_at` datetime(6) DEFAULT NULL,
  `trace_id` varchar(40) COLLATE utf8mb4_bin DEFAULT NULL,
  `deliver_at_epoch_ms` bigint DEFAULT NULL,
  PRIMARY KEY (`event_id`),
  UNIQUE KEY `uk_order_message_trace_type` (`trace_id`,`event_type`),
  KEY `idx_order_message_pending` (`status`,`next_attempt_at`,`locked_until`),
  CONSTRAINT `fk_order_message_outbox_trace_id` FOREIGN KEY (`trace_id`) REFERENCES `order_submit_trace` (`trace_id`),
  CONSTRAINT `chk_order_message_status` CHECK ((`status` in (_utf8mb4'PENDING',_utf8mb4'SENDING',_utf8mb4'SENT',_utf8mb4'CANCELLED'))),
  CONSTRAINT `chk_outbox_delivery_time` CHECK (((`deliver_at_epoch_ms` is null) or (`deliver_at_epoch_ms` > 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- order_submit_trace
CREATE TABLE `order_submit_trace` (
  `trace_id` varchar(40) COLLATE utf8mb4_bin NOT NULL,
  `user_id` bigint NOT NULL,
  `origin_key` varchar(80) COLLATE utf8mb4_bin DEFAULT NULL,
  `mode` varchar(8) COLLATE utf8mb4_bin NOT NULL,
  `request_snapshot` mediumtext COLLATE utf8mb4_bin NOT NULL,
  `request_hash` char(64) COLLATE utf8mb4_bin NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_bin NOT NULL DEFAULT 'INIT',
  `reserve_status` varchar(24) COLLATE utf8mb4_bin NOT NULL DEFAULT 'NONE',
  `order_id` varchar(40) COLLATE utf8mb4_bin DEFAULT NULL,
  `result_json` mediumtext COLLATE utf8mb4_bin,
  `failure_code` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `failure_reason` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  `expires_at` datetime(6) NOT NULL,
  `next_check_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `accepted_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `finished_at` datetime(6) DEFAULT NULL,
  `processing_expires_at` datetime(6) DEFAULT NULL,
  `submission_hash` char(64) COLLATE utf8mb4_bin DEFAULT NULL,
  PRIMARY KEY (`trace_id`),
  UNIQUE KEY `origin_key` (`origin_key`),
  KEY `idx_order_trace_recovery` (`status`,`reserve_status`,`next_check_at`),
  KEY `idx_order_trace_user` (`user_id`,`created_at`),
  KEY `idx_order_trace_pending` (`status`,`mode`,`next_check_at`,`processing_expires_at`),
  CONSTRAINT `fk_order_submit_trace_user_id` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`),
  CONSTRAINT `order_submit_trace_chk_1` CHECK ((`mode` in (_utf8mb4'SYNC',_utf8mb4'ASYNC'))),
  CONSTRAINT `order_submit_trace_chk_2` CHECK ((`status` in (_utf8mb4'INIT',_utf8mb4'PROCESSING',_utf8mb4'SUCCESS',_utf8mb4'FAILED',_utf8mb4'EXPIRED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- pending_action
CREATE TABLE `pending_action` (
  `id` varchar(40) COLLATE utf8mb4_bin NOT NULL,
  `idempotency_key` varchar(100) COLLATE utf8mb4_bin NOT NULL,
  `user_id` bigint NOT NULL,
  `conversation_id` varchar(40) COLLATE utf8mb4_bin NOT NULL,
  `tool_name` varchar(80) COLLATE utf8mb4_bin NOT NULL,
  `arguments` mediumtext COLLATE utf8mb4_bin NOT NULL,
  `arguments_hash` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `summary` varchar(500) COLLATE utf8mb4_bin NOT NULL,
  `status` varchar(24) COLLATE utf8mb4_bin NOT NULL,
  `result` mediumtext COLLATE utf8mb4_bin,
  `version` int NOT NULL DEFAULT '0',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `decided_at` datetime(6) DEFAULT NULL,
  `executed_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idempotency_key` (`idempotency_key`),
  KEY `fk_pending_action_user_id` (`user_id`),
  KEY `idx_pending_action_conversation` (`conversation_id`,`status`,`created_at`),
  CONSTRAINT `fk_pending_action_conversation_id` FOREIGN KEY (`conversation_id`) REFERENCES `conversation` (`id`),
  CONSTRAINT `fk_pending_action_user_id` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- product
CREATE TABLE `product` (
  `id` bigint NOT NULL,
  `name` varchar(160) COLLATE utf8mb4_bin NOT NULL,
  `category` varchar(60) COLLATE utf8mb4_bin NOT NULL,
  `brand` varchar(60) COLLATE utf8mb4_bin NOT NULL,
  `summary` varchar(500) COLLATE utf8mb4_bin NOT NULL,
  `image_key` varchar(40) COLLATE utf8mb4_bin NOT NULL,
  `rating` decimal(2,1) NOT NULL,
  `sales_count` int NOT NULL DEFAULT '0',
  `enabled` tinyint(1) NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`),
  KEY `idx_product_catalog_filter` (`category`,`brand`,`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- product_attribute
CREATE TABLE `product_attribute` (
  `product_id` bigint NOT NULL,
  `attr_key` varchar(60) COLLATE utf8mb4_bin NOT NULL,
  `attr_value` varchar(240) COLLATE utf8mb4_bin NOT NULL,
  PRIMARY KEY (`product_id`,`attr_key`),
  KEY `idx_product_attribute_filter` (`attr_key`,`attr_value`,`product_id`),
  CONSTRAINT `fk_product_attribute_product_id` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- product_sku
CREATE TABLE `product_sku` (
  `id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `sku_code` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `spec` varchar(160) COLLATE utf8mb4_bin NOT NULL,
  `price` decimal(12,2) NOT NULL,
  `original_price` decimal(12,2) DEFAULT NULL,
  `stock` int NOT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT '1',
  `locked_stock` int NOT NULL DEFAULT '0',
  `redis_inventory_initialized` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `sku_code` (`sku_code`),
  KEY `idx_sku_catalog_filter` (`product_id`,`enabled`,`price`),
  CONSTRAINT `fk_product_sku_product_id` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`),
  CONSTRAINT `chk_sku_nonnegative_stock` CHECK (((`stock` >= 0) and (`locked_stock` >= 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- sku_attribute
CREATE TABLE `sku_attribute` (
  `sku_id` bigint NOT NULL,
  `attr_key` varchar(60) COLLATE utf8mb4_bin NOT NULL,
  `attr_value` varchar(240) COLLATE utf8mb4_bin NOT NULL,
  PRIMARY KEY (`sku_id`,`attr_key`),
  KEY `idx_sku_attribute_filter` (`attr_key`,`attr_value`,`sku_id`),
  CONSTRAINT `fk_sku_attribute_sku_id` FOREIGN KEY (`sku_id`) REFERENCES `product_sku` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- user_address
CREATE TABLE `user_address` (
  `id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `receiver` varchar(80) COLLATE utf8mb4_bin NOT NULL,
  `phone` varchar(32) COLLATE utf8mb4_bin NOT NULL,
  `province` varchar(40) COLLATE utf8mb4_bin NOT NULL,
  `city` varchar(40) COLLATE utf8mb4_bin NOT NULL,
  `district` varchar(40) COLLATE utf8mb4_bin NOT NULL,
  `detail` varchar(200) COLLATE utf8mb4_bin NOT NULL,
  `is_default` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `fk_user_address_user_id` (`user_id`),
  CONSTRAINT `fk_user_address_user_id` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- user_coupon
CREATE TABLE `user_coupon` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `coupon_id` bigint NOT NULL,
  `status` varchar(24) COLLATE utf8mb4_bin NOT NULL DEFAULT 'AVAILABLE',
  `claimed_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `used_order_id` varchar(40) COLLATE utf8mb4_bin DEFAULT NULL,
  `used_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `user_id` (`user_id`,`coupon_id`),
  KEY `fk_user_coupon_coupon_id` (`coupon_id`),
  KEY `idx_user_coupon_user_status` (`user_id`,`status`),
  CONSTRAINT `fk_user_coupon_coupon_id` FOREIGN KEY (`coupon_id`) REFERENCES `coupon` (`id`),
  CONSTRAINT `fk_user_coupon_user_id` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- user_memory
CREATE TABLE `user_memory` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `memory_key` varchar(80) COLLATE utf8mb4_bin NOT NULL,
  `memory_value` varchar(500) COLLATE utf8mb4_bin NOT NULL,
  `source_conversation_id` varchar(40) COLLATE utf8mb4_bin DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `user_id` (`user_id`,`memory_key`),
  CONSTRAINT `fk_user_memory_user_id` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

SET FOREIGN_KEY_CHECKS=1;
