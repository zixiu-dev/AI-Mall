-- RAG 源文档与兼容索引｜当前 MySQL 结构（V23）
-- 由 scripts/export-database-schema.py 导出。按文件编号顺序导入空库可校验结构。
-- 应用初始化由 Flyway 执行，快照仅含结构、不含业务数据或 Flyway 历史。
SET NAMES utf8mb4;

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
