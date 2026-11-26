-- 用户与地址｜当前 MySQL 结构（V23）
-- 由 scripts/export-database-schema.py 导出。按文件编号顺序导入空库可校验结构。
-- 应用初始化由 Flyway 执行，快照仅含结构、不含业务数据或 Flyway 历史。
SET NAMES utf8mb4;

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
