# PostgreSQL 知识存储设计

采用 KnowledgeIndexStore 隔离后端。PGVECTOR 为应用默认后端；旧 JSON/Java 扫描仅 LEGACY 兼容选项。KEYWORD 仍可在没有 PG 时查询主库，HYBRID 的 PG 故障显式降级关键词或 ERROR。

PG 连接池和原生 MyBatis SqlSessionFactory 由知识仓库独立持有，按需初始化，不注册第二个全局 DataSource/事务管理器，不改变商城主库自动装配。PG Mapper 不加全局 @Mapper，SQL 全部在独立 XML；不引入生产 Spring JDBC 数据访问。

独立 db/pgvector/V1 存版本、chunk、发布指针，embedding 为 vector(1024)，metadata 为 JSONB，建立过滤 BTree 和 cosine HNSW 索引。源文档仍由现有主库和 manifest 管理，历史 V19 不修改、不删除。每次构建生成新 PG 版本；本次重建，不复制旧 JSON 向量。

模型请求在事务外。源文档行锁下复核哈希，PG 单库事务写全部块并切换指针；主库事务只读与锁定，不参与分布式写提交。查询固定发布身份、核对源内容/启用状态/模型，再从同版 PG chunk 执行关键词与向量召回。JVM 只加载有界的文本/哈希快照，不加载 PG 全量向量。

EXACT 使用 MATERIALIZED 合法集合 CTE 再计算余弦，保证过滤先于 topK。HNSW 使用 pgvector 0.8+ strict_order iterative scan，若返回少于 min(topK,合法组条数)，执行相同范围精确查询补齐；不承诺 ANN 与精确排名完全一致。小库查询计划可选择顺序扫描，不能仅凭建了索引就声称每次使用 HNSW。

单独 PG schema history 与连接配置；迁移/断线失败不接管主库，池创建失败关闭资源并允许后续重试。数据库查询与锁等待有界，继承检索总截止时间。保留去重/RRF/完整证据预算，不新建第二个 Agent。

PG 表和独立 Flyway history 放在 knowledge schema；vector 扩展安装于 public。这样 DBA 已预装扩展时不需要不安全地 baseline 跳过迁移，也不会把 public 其他对象当作本项目表。PG 连接的 search_path 固定 knowledge,public。
