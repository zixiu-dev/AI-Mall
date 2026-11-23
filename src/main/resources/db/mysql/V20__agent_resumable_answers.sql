-- 助手占位消息保存生成状态和发起请求的身份，历史消息的新增列为空。
ALTER TABLE chat_message ADD COLUMN generation_status VARCHAR(16);
ALTER TABLE chat_message ADD COLUMN reply_to_message_id BIGINT;
ALTER TABLE chat_message ADD COLUMN request_key VARCHAR(100);
CREATE UNIQUE INDEX uq_chat_message_request_key ON chat_message(request_key);
CREATE INDEX idx_chat_message_generation ON chat_message(generation_status, conversation_id, id);
