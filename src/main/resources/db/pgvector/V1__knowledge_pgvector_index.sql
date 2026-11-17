-- 此迁移只由知识 PG 连接执行；不加入商城主库 Flyway 路径。
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
CREATE TABLE knowledge_index_head(head_id INTEGER PRIMARY KEY CHECK(head_id=1),index_version VARCHAR(80) REFERENCES knowledge_index_version(index_version));
INSERT INTO knowledge_index_head(head_id,index_version) VALUES(1,NULL);
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
