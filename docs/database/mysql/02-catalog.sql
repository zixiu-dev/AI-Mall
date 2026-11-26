-- 商品、SKU 与属性｜当前 MySQL 结构（V23）
-- 由 scripts/export-database-schema.py 导出。按文件编号顺序导入空库可校验结构。
-- 应用初始化由 Flyway 执行，快照仅含结构、不含业务数据或 Flyway 历史。
SET NAMES utf8mb4;

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
