-- 订单消息｜当前 MySQL 结构（V23）
-- 由 scripts/export-database-schema.py 导出。按文件编号顺序导入空库可校验结构。
-- 应用初始化由 Flyway 执行，快照仅含结构、不含业务数据或 Flyway 历史。
SET NAMES utf8mb4;

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
