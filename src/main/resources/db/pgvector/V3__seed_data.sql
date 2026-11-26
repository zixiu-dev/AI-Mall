-- 新建向量库的单行发布指针，首次发布后再绑定索引版本。
INSERT INTO knowledge_index_head(head_id, index_version)
SELECT 1, NULL
WHERE EXISTS (
    SELECT 1 FROM knowledge."${flyway:table}"
    WHERE version = '2' AND type = 'SQL_BASELINE'
);
