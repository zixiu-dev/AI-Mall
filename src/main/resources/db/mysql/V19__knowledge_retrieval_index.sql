-- 候选索引与发布指针分离；只有完成的同模型版本可被查询读取。
CREATE TABLE knowledge_index_version (
  index_version VARCHAR(80) PRIMARY KEY,
  build_status VARCHAR(20) NOT NULL,
  embedding_model_id VARCHAR(200) NOT NULL,
  embedding_version VARCHAR(120) NOT NULL,
  dimension INT NOT NULL,
  document_count INT NOT NULL,
  chunk_count INT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  published_at TIMESTAMP NULL
);
CREATE TABLE knowledge_index_head (
  head_id INT PRIMARY KEY,
  index_version VARCHAR(80) NULL,
  FOREIGN KEY (index_version) REFERENCES knowledge_index_version(index_version)
);
INSERT INTO knowledge_index_head(head_id,index_version) VALUES (1,NULL);
CREATE TABLE knowledge_index_chunk (
  index_version VARCHAR(80) NOT NULL,
  chunk_id VARCHAR(100) NOT NULL,
  document_key VARCHAR(100) NOT NULL,
  content_version VARCHAR(80) NOT NULL,
  document_hash VARCHAR(64) NOT NULL,
  text_hash VARCHAR(64) NOT NULL,
  section_title VARCHAR(300) NOT NULL,
  chunk_text TEXT NOT NULL,
  vector_json MEDIUMTEXT NOT NULL,
  metadata_json TEXT NOT NULL,
  brand_code VARCHAR(32) NOT NULL,
  document_type VARCHAR(32) NOT NULL,
  region VARCHAR(40) NOT NULL,
  PRIMARY KEY (index_version,chunk_id),
  FOREIGN KEY (index_version) REFERENCES knowledge_index_version(index_version)
);
CREATE INDEX idx_knowledge_chunk_scope ON knowledge_index_chunk(index_version,brand_code,document_type,region);
