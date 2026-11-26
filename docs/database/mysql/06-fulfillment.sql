-- 物流与售后｜当前 MySQL 结构（V23）
-- 由 scripts/export-database-schema.py 导出。按文件编号顺序导入空库可校验结构。
-- 应用初始化由 Flyway 执行，快照仅含结构、不含业务数据或 Flyway 历史。
SET NAMES utf8mb4;

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
