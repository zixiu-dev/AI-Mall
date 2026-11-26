-- 新建库的最终建表定义，包含中文注释、索引及约束。
-- 已有 Flyway 历史的库沿独立迁移历史执行升级。

CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public;
CREATE TABLE knowledge_index_version (
  index_version VARCHAR(80) PRIMARY KEY,
  build_status VARCHAR(20) NOT NULL CHECK(build_status IN ('BUILDING','PUBLISHED','FAILED')),
  embedding_model_id VARCHAR(200) NOT NULL,
  embedding_version VARCHAR(120) NOT NULL,
  dimension INTEGER NOT NULL CHECK(dimension=1024),
  document_count INTEGER NOT NULL,
  chunk_count INTEGER NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  published_at TIMESTAMPTZ
);
CREATE TABLE knowledge_index_head (
  head_id INTEGER PRIMARY KEY CHECK(head_id=1),
  index_version VARCHAR(80) REFERENCES knowledge_index_version(index_version)
);
CREATE TABLE knowledge_index_chunk (
  index_version VARCHAR(80) NOT NULL REFERENCES knowledge_index_version(index_version),
  chunk_id VARCHAR(100) NOT NULL,
  document_key VARCHAR(100) NOT NULL,
  content_version VARCHAR(80) NOT NULL,
  document_hash VARCHAR(64) NOT NULL,
  text_hash VARCHAR(64) NOT NULL,
  section_title VARCHAR(300) NOT NULL,
  chunk_text TEXT NOT NULL,
  embedding vector(1024) NOT NULL CHECK(vector_norm(embedding)>0),
  metadata_json JSONB NOT NULL,
  brand_code VARCHAR(32) NOT NULL,
  document_type VARCHAR(32) NOT NULL,
  region VARCHAR(40) NOT NULL,
  PRIMARY KEY(index_version,chunk_id)
);
CREATE INDEX idx_pgvector_chunk_scope ON knowledge_index_chunk(index_version,brand_code,document_type,region);
CREATE INDEX idx_pgvector_chunk_hnsw ON knowledge_index_chunk USING hnsw(embedding vector_cosine_ops);

COMMENT ON TABLE knowledge.knowledge_index_version IS 'PostgreSQL 向量索引的构建与发布版本';
COMMENT ON COLUMN knowledge.knowledge_index_version.index_version IS '单次索引构建的版本标识';
COMMENT ON COLUMN knowledge.knowledge_index_version.build_status IS '构建状态，如 BUILDING、PUBLISHED 或 FAILED';
COMMENT ON COLUMN knowledge.knowledge_index_version.embedding_model_id IS '生成此索引的向量模型标识';
COMMENT ON COLUMN knowledge.knowledge_index_version.embedding_version IS '向量配置的版本标识';
COMMENT ON COLUMN knowledge.knowledge_index_version.dimension IS '向量维度';
COMMENT ON COLUMN knowledge.knowledge_index_version.document_count IS '此索引覆盖的源文档数量';
COMMENT ON COLUMN knowledge.knowledge_index_version.chunk_count IS '此索引包含的文本切片数量';
COMMENT ON COLUMN knowledge.knowledge_index_version.created_at IS '索引构建记录创建时间';
COMMENT ON COLUMN knowledge.knowledge_index_version.published_at IS '此版本发布的时间';

COMMENT ON TABLE knowledge.knowledge_index_head IS 'PostgreSQL 当前已发布向量索引版本的指针';
COMMENT ON COLUMN knowledge.knowledge_index_head.head_id IS '发布指针固定记录键';
COMMENT ON COLUMN knowledge.knowledge_index_head.index_version IS '当前可用的索引版本，尚未发布时为空';

COMMENT ON TABLE knowledge.knowledge_index_chunk IS 'PostgreSQL 知识切片及 pgvector 向量索引';
COMMENT ON COLUMN knowledge.knowledge_index_chunk.index_version IS '切片归属的索引版本';
COMMENT ON COLUMN knowledge.knowledge_index_chunk.chunk_id IS '索引版本内的切片标识';
COMMENT ON COLUMN knowledge.knowledge_index_chunk.document_key IS '来源文档的稳定标识';
COMMENT ON COLUMN knowledge.knowledge_index_chunk.content_version IS '源文档正文版本';
COMMENT ON COLUMN knowledge.knowledge_index_chunk.document_hash IS '切分时的源文档摘要';
COMMENT ON COLUMN knowledge.knowledge_index_chunk.text_hash IS '当前切片正文摘要';
COMMENT ON COLUMN knowledge.knowledge_index_chunk.section_title IS '切片所在文档小节标题';
COMMENT ON COLUMN knowledge.knowledge_index_chunk.chunk_text IS '供检索和引用的切片正文';
COMMENT ON COLUMN knowledge.knowledge_index_chunk.embedding IS '用于余弦距离召回的 1024 维 pgvector 嵌入';
COMMENT ON COLUMN knowledge.knowledge_index_chunk.metadata_json IS '构建时记录的来源、渠道等引用属性 JSON';
COMMENT ON COLUMN knowledge.knowledge_index_chunk.brand_code IS '供检索过滤的品牌编码';
COMMENT ON COLUMN knowledge.knowledge_index_chunk.document_type IS '供检索过滤的政策类型';
COMMENT ON COLUMN knowledge.knowledge_index_chunk.region IS '供检索过滤的地区编码';
