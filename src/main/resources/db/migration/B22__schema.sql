-- 新建库的最终建表定义，包含中文注释、索引及约束。
-- 已有 Flyway 历史的库沿独立迁移历史执行升级。

CREATE TABLE "app_user" COMMENT '商城用户，作为订单、地址、优惠券与会话的归属主体'(
    "id" BIGINT COMMENT '用户编号' NOT NULL,
    "nickname" CHARACTER VARYING(80) COMMENT '用户展示昵称' NOT NULL,
    "phone" CHARACTER VARYING(32) COMMENT '用户联系电话' NOT NULL,
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '用户记录创建时间' NOT NULL
);
ALTER TABLE "app_user" ADD CONSTRAINT "CONSTRAINT_4" PRIMARY KEY("id");
CREATE TABLE "user_address" COMMENT '用户的收货地址，建单时将所选地址复制到订单快照'(
    "id" BIGINT COMMENT '地址编号' NOT NULL,
    "user_id" BIGINT COMMENT '地址所属用户编号' NOT NULL,
    "receiver" CHARACTER VARYING(80) COMMENT '收货人姓名' NOT NULL,
    "phone" CHARACTER VARYING(32) COMMENT '收货联系电话' NOT NULL,
    "province" CHARACTER VARYING(40) COMMENT '省级行政区' NOT NULL,
    "city" CHARACTER VARYING(40) COMMENT '市级行政区' NOT NULL,
    "district" CHARACTER VARYING(40) COMMENT '区县' NOT NULL,
    "detail" CHARACTER VARYING(200) COMMENT '街道门牌等详细地址' NOT NULL,
    "is_default" BOOLEAN DEFAULT FALSE COMMENT '是否为用户默认收货地址' NOT NULL
);
ALTER TABLE "user_address" ADD CONSTRAINT "CONSTRAINT_1" PRIMARY KEY("id");
CREATE TABLE "product" COMMENT '商品主档，保存同一商品各规格共享的展示信息'(
    "id" BIGINT COMMENT '商品编号' NOT NULL,
    "name" CHARACTER VARYING(160) COMMENT '商品展示名称' NOT NULL,
    "category" CHARACTER VARYING(60) COMMENT '商品类目' NOT NULL,
    "brand" CHARACTER VARYING(60) COMMENT '商品品牌' NOT NULL,
    "summary" CHARACTER VARYING(500) COMMENT '商品简短介绍' NOT NULL,
    "image_key" CHARACTER VARYING(40) COMMENT '商城图片资源键' NOT NULL,
    "rating" NUMERIC(2, 1) COMMENT '商品展示评分' NOT NULL,
    "sales_count" INTEGER DEFAULT 0 COMMENT '商品展示销量' NOT NULL,
    "enabled" BOOLEAN DEFAULT TRUE COMMENT '商品是否在商城启用' NOT NULL
);
ALTER TABLE "product" ADD CONSTRAINT "CONSTRAINT_E" PRIMARY KEY("id");
CREATE INDEX "idx_product_catalog_filter" ON "product"("category" NULLS FIRST, "brand" NULLS FIRST, "enabled" NULLS FIRST);
CREATE TABLE "order_submit_trace" COMMENT '一次购买意图的持久化记录，保存幂等关联、购买结果和库存整理进度'(
    "trace_id" CHARACTER VARYING(40) COMMENT '购买意图编号，前端按此查询建单结果' NOT NULL,
    "user_id" BIGINT COMMENT '发起购买的用户编号' NOT NULL,
    "origin_key" CHARACTER VARYING(80) COMMENT '购买来源唯一键，如 Agent 确认操作关联键',
    "mode" CHARACTER VARYING(8) COMMENT '实际建单模式，SYNC 同步或 ASYNC 异步' NOT NULL,
    "request_snapshot" CHARACTER VARYING COMMENT '持久化的购买与结算快照 JSON，供后续建单和恢复使用' NOT NULL,
    "request_hash" CHARACTER(64) COMMENT '购买快照摘要，用于核对原始意图' NOT NULL,
    "status" CHARACTER VARYING(20) DEFAULT 'INIT' COMMENT '购买状态，INIT、PROCESSING、SUCCESS、FAILED 或 EXPIRED' NOT NULL,
    "reserve_status" CHARACTER VARYING(24) DEFAULT 'NONE' COMMENT 'Redis 库存预占及后续确认、释放、返还的进度' NOT NULL,
    "order_id" CHARACTER VARYING(40) COMMENT '成功创建的订单编号',
    "result_json" CHARACTER VARYING COMMENT '已保存的建单结果 JSON，重复查询可复用',
    "failure_code" CHARACTER VARYING(64) COMMENT '购买失败的业务错误码',
    "failure_reason" CHARACTER VARYING(500) COMMENT '可展示的购买失败原因',
    "version" INTEGER DEFAULT 0 COMMENT '用于状态条件更新的并发版本号' NOT NULL,
    "expires_at" TIMESTAMP COMMENT '购买意图在受理前的有效期截止时间' NOT NULL,
    "next_check_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '后台下次检查或恢复此购买的最早时间' NOT NULL,
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '购买意图创建时间' NOT NULL,
    "accepted_at" TIMESTAMP COMMENT '可靠受理购买的时间，可早于真实建单',
    "updated_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '最近状态更新时间' NOT NULL,
    "finished_at" TIMESTAMP COMMENT '写入购买终态的时间',
    "processing_expires_at" TIMESTAMP COMMENT '异步受理后实际建单的处理截止时间',
    "submission_hash" CHARACTER(64) COMMENT '首次提交参数的摘要，用于核对同一购买编号的重试'
);
ALTER TABLE "order_submit_trace" ADD CONSTRAINT "CONSTRAINT_3" PRIMARY KEY("trace_id");
CREATE INDEX "idx_order_trace_recovery" ON "order_submit_trace"("status" NULLS FIRST, "reserve_status" NULLS FIRST, "next_check_at" NULLS FIRST);
CREATE INDEX "idx_order_trace_user" ON "order_submit_trace"("user_id" NULLS FIRST, "created_at" NULLS FIRST);
CREATE INDEX "idx_order_trace_pending" ON "order_submit_trace"("status" NULLS FIRST, "mode" NULLS FIRST, "next_check_at" NULLS FIRST, "processing_expires_at" NULLS FIRST);
CREATE TABLE "customer_order" COMMENT '订单主表，保存订单金额、状态、收货和支付期限快照'(
    "id" CHARACTER VARYING(40) COMMENT '订单编号' NOT NULL,
    "user_id" BIGINT COMMENT '下单用户编号' NOT NULL,
    "status" CHARACTER VARYING(32) COMMENT '订单生命周期状态，如 PENDING_PAYMENT、PAID、CANCELLED' NOT NULL,
    "goods_amount" NUMERIC(12, 2) COMMENT '优惠前商品总金额，单位元' NOT NULL,
    "discount_amount" NUMERIC(12, 2) DEFAULT 0 COMMENT '本订单实际抵扣金额，单位元' NOT NULL,
    "freight_amount" NUMERIC(12, 2) DEFAULT 0 COMMENT '订单运费，单位元' NOT NULL,
    "pay_amount" NUMERIC(12, 2) COMMENT '最终应付金额，单位元' NOT NULL,
    "address_snapshot" CHARACTER VARYING(500) COMMENT '下单时的收货地址快照' NOT NULL,
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '成功建单时间' NOT NULL,
    "paid_at" TIMESTAMP COMMENT '支付成功时间，未支付时为空',
    "cancelled_at" TIMESTAMP COMMENT '订单被取消或超时关闭的时间',
    "version" INTEGER DEFAULT 0 COMMENT '用于订单并发状态更新的版本号' NOT NULL,
    "coupon_id" BIGINT COMMENT '下单使用的优惠券模板编号，未用券时为空',
    "coupon_name" CHARACTER VARYING(120) COMMENT '下单时的优惠券名称快照',
    "submit_trace_id" CHARACTER VARYING(40) COMMENT '来源购买意图编号，唯一约束保证同一 trace 至多建一单',
    "payment_expires_at" TIMESTAMP COMMENT '自建单起计算的支付截止时间'
);
ALTER TABLE "customer_order" ADD CONSTRAINT "CONSTRAINT_8" PRIMARY KEY("id");
CREATE INDEX "idx_order_payment_timeout" ON "customer_order"("status" NULLS FIRST, "payment_expires_at" NULLS FIRST);
CREATE TABLE "order_inventory_return" COMMENT '关闭未支付订单后的 Redis 库存返还任务'(
    "order_id" CHARACTER VARYING(40) COMMENT '已关闭订单编号' NOT NULL,
    "sku_id" BIGINT COMMENT '待返还的 SKU 编号' NOT NULL,
    "reservation_id" CHARACTER VARYING(90) COMMENT '库存预占标识，通常是购买 trace，旧单可用派生标识' NOT NULL,
    "quantity" INTEGER COMMENT '待返还到 Redis 可售池的件数' NOT NULL,
    "completed" BOOLEAN DEFAULT FALSE COMMENT 'Redis 返还任务是否完成' NOT NULL,
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '订单关闭事务创建返还任务的时间' NOT NULL
);
ALTER TABLE "order_inventory_return" ADD CONSTRAINT "CONSTRAINT_9A05C" PRIMARY KEY("order_id", "sku_id");
CREATE INDEX "idx_inventory_return_pending" ON "order_inventory_return"("completed" NULLS FIRST, "created_at" NULLS FIRST);
CREATE TABLE "order_item" COMMENT '订单商品行，保留成交时的商品、规格、单价和数量'(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1 RESTART WITH 3) COMMENT '订单商品行编号' NOT NULL,
    "order_id" CHARACTER VARYING(40) COMMENT '所属订单编号' NOT NULL,
    "product_id" BIGINT COMMENT '成交商品编号' NOT NULL,
    "sku_id" BIGINT COMMENT '成交 SKU 编号' NOT NULL,
    "product_name" CHARACTER VARYING(160) COMMENT '成交时的商品名称快照' NOT NULL,
    "sku_spec" CHARACTER VARYING(160) COMMENT '成交时的规格名称快照' NOT NULL,
    "unit_price" NUMERIC(12, 2) COMMENT '成交时的商品单价，单位元' NOT NULL,
    "quantity" INTEGER COMMENT '此订单行的购买件数' NOT NULL
);
ALTER TABLE "order_item" ADD CONSTRAINT "CONSTRAINT_2" PRIMARY KEY("id");
CREATE TABLE "logistics_event" COMMENT '按订单记录物流时间线事件'(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1 RESTART WITH 5) COMMENT '物流事件编号' NOT NULL,
    "order_id" CHARACTER VARYING(40) COMMENT '物流事件所属订单编号' NOT NULL,
    "event_time" TIMESTAMP COMMENT '物流节点发生时间' NOT NULL,
    "status" CHARACTER VARYING(40) COMMENT '物流事件状态' NOT NULL,
    "description" CHARACTER VARYING(500) COMMENT '物流节点的展示描述' NOT NULL
);
ALTER TABLE "logistics_event" ADD CONSTRAINT "CONSTRAINT_F" PRIMARY KEY("id");
CREATE TABLE "knowledge_index_version" COMMENT 'MySQL LEGACY 兼容索引的构建与发布版本'(
    "index_version" CHARACTER VARYING(80) COMMENT '单次索引构建的版本标识' NOT NULL,
    "build_status" CHARACTER VARYING(20) COMMENT '构建状态，如 BUILDING、PUBLISHED 或 FAILED' NOT NULL,
    "embedding_model_id" CHARACTER VARYING(200) COMMENT '生成此索引的向量模型标识' NOT NULL,
    "embedding_version" CHARACTER VARYING(120) COMMENT '向量配置的版本标识' NOT NULL,
    "dimension" INTEGER COMMENT '向量维度' NOT NULL,
    "document_count" INTEGER COMMENT '此索引覆盖的源文档数量' NOT NULL,
    "chunk_count" INTEGER COMMENT '此索引包含的文本切片数量' NOT NULL,
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '索引构建记录创建时间' NOT NULL,
    "published_at" TIMESTAMP COMMENT '此版本发布的时间'
);
ALTER TABLE "knowledge_index_version" ADD CONSTRAINT "CONSTRAINT_9B" PRIMARY KEY("index_version");
CREATE TABLE "after_sales" COMMENT '用户提交的售后申请及当前处理进度'(
    "id" CHARACTER VARYING(40) COMMENT '售后申请编号' NOT NULL,
    "order_id" CHARACTER VARYING(40) COMMENT '申请对应的订单编号' NOT NULL,
    "user_id" BIGINT COMMENT '发起售后的用户编号' NOT NULL,
    "type" CHARACTER VARYING(32) COMMENT '售后申请类型' NOT NULL,
    "reason" CHARACTER VARYING(500) COMMENT '用户填写的售后原因' NOT NULL,
    "status" CHARACTER VARYING(32) COMMENT '售后申请处理状态' NOT NULL,
    "refund_amount" NUMERIC(12, 2) COMMENT '申请记录中的退款金额，单位元，可为空',
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '售后申请创建时间' NOT NULL,
    "updated_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '最近处理更新时间' NOT NULL
);
ALTER TABLE "after_sales" ADD CONSTRAINT "CONSTRAINT_21" PRIMARY KEY("id");
CREATE TABLE "knowledge_document" COMMENT 'RAG 源文档，保存政策正文、来源和适用范围'(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1 RESTART WITH 7) COMMENT '源文档内部编号' NOT NULL,
    "category" CHARACTER VARYING(60) COMMENT '文档的可读业务分类' NOT NULL,
    "title" CHARACTER VARYING(200) COMMENT '政策文档标题' NOT NULL,
    "keywords" CHARACTER VARYING(300) COMMENT '关键词检索使用的预置词' NOT NULL,
    "content" CHARACTER VARYING COMMENT '政策正文' NOT NULL,
    "enabled" BOOLEAN DEFAULT TRUE COMMENT '文档是否可参与当前检索或索引构建' NOT NULL,
    "brand_code" CHARACTER VARYING(32) DEFAULT 'PLATFORM' COMMENT '适用品牌编码，PLATFORM 代表商城自身政策' NOT NULL,
    "document_type" CHARACTER VARYING(32) DEFAULT 'GENERAL' COMMENT '政策类型，如退换货、退款到账或保修' NOT NULL,
    "document_key" CHARACTER VARYING(100) COMMENT '用于索引及更新的稳定文档标识',
    "source_url" CHARACTER VARYING(500) COMMENT '政策来源链接',
    "source_name" CHARACTER VARYING(160) COMMENT '政策发布方或出处名称',
    "source_checked_at" DATE COMMENT '最近核对来源的日期',
    "applicable_channel" CHARACTER VARYING(300) COMMENT '文档适用的销售或服务渠道描述',
    "region" CHARACTER VARYING(40) DEFAULT 'CN_MAINLAND' COMMENT '政策适用地区编码' NOT NULL,
    "content_version" CHARACTER VARYING(80) COMMENT '当前正文内容的版本',
    "scope_note" CHARACTER VARYING(500) COMMENT '适用范围与例外补充说明'
);
ALTER TABLE "knowledge_document" ADD CONSTRAINT "CONSTRAINT_4B" PRIMARY KEY("id");
CREATE INDEX "idx_knowledge_brand_type" ON "knowledge_document"("enabled" NULLS FIRST, "brand_code" NULLS FIRST, "document_type" NULLS FIRST, "region" NULLS FIRST);
CREATE TABLE "agent_skill" COMMENT 'Agent 可按需加载的业务技能手册和关联工具'(
    "code" CHARACTER VARYING(64) COMMENT '技能的稳定标识，用于 load_skill 和校验' NOT NULL,
    "name" CHARACTER VARYING(100) COMMENT '技能展示名称' NOT NULL,
    "description" CHARACTER VARYING(300) COMMENT '何时适合加载此技能的简短说明' NOT NULL,
    "manual" CHARACTER VARYING COMMENT '技能办理手册正文' NOT NULL,
    "tool_names" CHARACTER VARYING COMMENT '技能对应的工具名称列表 JSON' NOT NULL,
    "enabled" BOOLEAN DEFAULT TRUE COMMENT '技能是否启用' NOT NULL
);
ALTER TABLE "agent_skill" ADD CONSTRAINT "CONSTRAINT_7" PRIMARY KEY("code");
CREATE TABLE "conversation" COMMENT 'Agent 会话主表，一个会话可包含多轮问答'(
    "id" CHARACTER VARYING(40) COMMENT '会话编号，对应监控页的 Thread ID' NOT NULL,
    "user_id" BIGINT COMMENT '会话所属用户编号' NOT NULL,
    "title" CHARACTER VARYING(160) DEFAULT '新会话' COMMENT '会话展示标题' NOT NULL,
    "summary" CHARACTER VARYING COMMENT '较早轮次的滚动摘要',
    "summarized_until_id" BIGINT COMMENT '已包含在摘要中的最后一条消息编号',
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '会话创建时间' NOT NULL,
    "updated_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '最近更新或新增消息的时间' NOT NULL
);
ALTER TABLE "conversation" ADD CONSTRAINT "CONSTRAINT_2C" PRIMARY KEY("id");
CREATE TABLE "agent_model_call" COMMENT 'Agent 每次真实模型请求的诊断明细'(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1) COMMENT '模型调用明细主键' NOT NULL,
    "turn_id" BIGINT COMMENT '后台助手消息编号，即所在轮次' NOT NULL,
    "sequence_no" INTEGER COMMENT '同轮事件序号，与执行项共用排序口径' NOT NULL,
    "step_no" INTEGER COMMENT 'Agent 循环中的推理步骤编号' NOT NULL,
    "attempt_no" INTEGER COMMENT '同一步内实际模型请求的尝试编号' NOT NULL,
    "candidate" CHARACTER VARYING(24) COMMENT '模型候选角色，如 primary 或 bailian' NOT NULL,
    "model" CHARACTER VARYING(160) COMMENT '实际请求的模型标识' NOT NULL,
    "status" CHARACTER VARYING(24) COMMENT '此次模型请求的结果状态' NOT NULL,
    "error_code" CHARACTER VARYING(64) COMMENT '安全归类后的模型失败代码',
    "started_at" TIMESTAMP(6) COMMENT '调用开始时间，按 UTC 记录' NOT NULL,
    "duration_ms" BIGINT COMMENT '调用总耗时，单位毫秒' NOT NULL,
    "first_text_ms" BIGINT COMMENT '首段文本耗时，未收到文字时为空',
    "input_tokens" INTEGER COMMENT '上游回传的输入 Token，未回传时为空',
    "output_tokens" INTEGER COMMENT '上游回传的输出 Token，未回传时为空'
);
ALTER TABLE "agent_model_call" ADD CONSTRAINT "CONSTRAINT_73" PRIMARY KEY("id");
CREATE INDEX "idx_agent_model_call_retention" ON "agent_model_call"("started_at" NULLS FIRST, "id" NULLS FIRST);
CREATE TABLE "conversation_skill" COMMENT '会话与已加载 Agent 技能的关联'(
    "conversation_id" CHARACTER VARYING(40) COMMENT '加载技能的会话编号' NOT NULL,
    "skill_code" CHARACTER VARYING(64) COMMENT '被加载的技能编码' NOT NULL,
    "loaded_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '技能加载记录的写入时间' NOT NULL
);
ALTER TABLE "conversation_skill" ADD CONSTRAINT "CONSTRAINT_77BC" PRIMARY KEY("conversation_id", "skill_code");
CREATE TABLE "pending_action" COMMENT 'Agent 待用户确认的操作，保存可信参数及确认执行结果'(
    "id" CHARACTER VARYING(40) COMMENT '待确认操作编号' NOT NULL,
    "idempotency_key" CHARACTER VARYING(100) COMMENT '创建待确认操作时使用的幂等键' NOT NULL,
    "user_id" BIGINT COMMENT '有权确认的用户编号' NOT NULL,
    "conversation_id" CHARACTER VARYING(40) COMMENT '创建该操作的会话编号' NOT NULL,
    "tool_name" CHARACTER VARYING(80) COMMENT '待确认的写工具名称' NOT NULL,
    "arguments" CHARACTER VARYING COMMENT '待确认操作的规范化参数 JSON' NOT NULL,
    "arguments_hash" CHARACTER VARYING(64) COMMENT '参数摘要，用于识别操作参数' NOT NULL,
    "summary" CHARACTER VARYING(500) COMMENT '确认卡向用户展示的操作摘要' NOT NULL,
    "status" CHARACTER VARYING(24) COMMENT '待确认操作的处理状态' NOT NULL,
    "result" CHARACTER VARYING COMMENT '确认执行或失败后的结果 JSON',
    "version" INTEGER DEFAULT 0 COMMENT '用于确认状态更新的并发版本号' NOT NULL,
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '确认操作的创建时间' NOT NULL,
    "decided_at" TIMESTAMP COMMENT '用户作出确认或拒绝决定的时间',
    "executed_at" TIMESTAMP COMMENT '记录实际执行结果的时间'
);
ALTER TABLE "pending_action" ADD CONSTRAINT "CONSTRAINT_6" PRIMARY KEY("id");
CREATE INDEX "idx_pending_action_conversation" ON "pending_action"("conversation_id" NULLS FIRST, "status" NULLS FIRST, "created_at" NULLS FIRST);
CREATE TABLE "user_memory" COMMENT '用户明确要求长期保留的购物偏好'(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1 RESTART WITH 3) COMMENT '用户偏好记录编号' NOT NULL,
    "user_id" BIGINT COMMENT '偏好所属用户编号' NOT NULL,
    "memory_key" CHARACTER VARYING(80) COMMENT '偏好键，同一用户下唯一' NOT NULL,
    "memory_value" CHARACTER VARYING(500) COMMENT '用户授权记录的偏好内容' NOT NULL,
    "source_conversation_id" CHARACTER VARYING(40) COMMENT '形成这条偏好的来源会话编号',
    "version" INTEGER DEFAULT 0 COMMENT '偏好记录版本' NOT NULL,
    "updated_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '偏好最近更新时间' NOT NULL
);
ALTER TABLE "user_memory" ADD CONSTRAINT "CONSTRAINT_EB" PRIMARY KEY("id");
CREATE TABLE "coupon" COMMENT '优惠券模板，定义领取额度、有效期和满减规则'(
    "id" BIGINT COMMENT '优惠券模板编号' NOT NULL,
    "name" CHARACTER VARYING(120) COMMENT '优惠券展示名称' NOT NULL,
    "threshold_amount" NUMERIC(12, 2) DEFAULT 0 COMMENT '核销所需的最低商品金额，单位元' NOT NULL,
    "discount_amount" NUMERIC(12, 2) COMMENT '模板面额，单位元' NOT NULL,
    "total_stock" INTEGER COMMENT '模板可领取份数上限' NOT NULL,
    "claimed_count" INTEGER DEFAULT 0 COMMENT '模板已领取份数' NOT NULL,
    "start_at" TIMESTAMP COMMENT '生效时间' NOT NULL,
    "end_at" TIMESTAMP COMMENT '失效时间' NOT NULL,
    "status" CHARACTER VARYING(24) DEFAULT 'ACTIVE' COMMENT '模板启用状态，ACTIVE 表示启用' NOT NULL
);
ALTER TABLE "coupon" ADD CONSTRAINT "CONSTRAINT_AF" PRIMARY KEY("id");
CREATE TABLE "user_coupon" COMMENT '用户已领的具体优惠券，记录占用与核销'(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1 RESTART WITH 2) COMMENT '用户券编号，建单选券使用此编号' NOT NULL,
    "user_id" BIGINT COMMENT '领券用户编号' NOT NULL,
    "coupon_id" BIGINT COMMENT '来源优惠券模板编号' NOT NULL,
    "status" CHARACTER VARYING(24) DEFAULT 'AVAILABLE' COMMENT '用户券状态，如 AVAILABLE 可用、LOCKED 已占用、USED 已核销' NOT NULL,
    "claimed_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '用户领取时间' NOT NULL,
    "used_order_id" CHARACTER VARYING(40) COMMENT '占用或使用这张券的订单编号',
    "used_at" TIMESTAMP COMMENT '支付时核销优惠券的时间'
);
ALTER TABLE "user_coupon" ADD CONSTRAINT "CONSTRAINT_D" PRIMARY KEY("id");
CREATE INDEX "idx_user_coupon_user_status" ON "user_coupon"("user_id" NULLS FIRST, "status" NULLS FIRST);
CREATE TABLE "cart_item" COMMENT '购物车中的用户 SKU、数量和勾选状态'(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1 RESTART WITH 3) COMMENT '购物车项编号' NOT NULL,
    "user_id" BIGINT COMMENT '购物车所属用户编号' NOT NULL,
    "sku_id" BIGINT COMMENT '加入购物车的 SKU 编号' NOT NULL,
    "quantity" INTEGER COMMENT '购物车中的购买数量' NOT NULL,
    "selected" BOOLEAN DEFAULT TRUE COMMENT '是否勾选参与购物车结算' NOT NULL,
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '首次加入购物车的时间' NOT NULL,
    "updated_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '最近修改购物车项的时间' NOT NULL
);
ALTER TABLE "cart_item" ADD CONSTRAINT "CONSTRAINT_C" PRIMARY KEY("id");
CREATE INDEX "idx_cart_item_user" ON "cart_item"("user_id" NULLS FIRST, "selected" NULLS FIRST);
CREATE TABLE "agent_scope_state" COMMENT 'Agent 模型运行状态，当前 Spring AI 消息沿用此历史表名'(
    "user_id" CHARACTER VARYING(80) COMMENT 'Agent 运行状态所属用户的字符串标识' NOT NULL,
    "session_id" CHARACTER VARYING(80) COMMENT 'Agent 运行状态所属会话标识' NOT NULL,
    "state_key" CHARACTER VARYING(120) COMMENT '运行状态键，当前使用 spring-ai-v1 存放模型消息' NOT NULL,
    "payload" CHARACTER VARYING COMMENT '序列化的模型上下文及工具消息' NOT NULL,
    "updated_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '运行状态最近写入时间' NOT NULL
);
ALTER TABLE "agent_scope_state" ADD CONSTRAINT "CONSTRAINT_60" PRIMARY KEY("user_id", "session_id", "state_key");
CREATE TABLE "product_attribute" COMMENT '商品级扩展属性，可用于展示或检索'(
    "product_id" BIGINT COMMENT '属性所属商品编号' NOT NULL,
    "attr_key" CHARACTER VARYING(60) COMMENT '属性键，如材质、用途或来源链接键' NOT NULL,
    "attr_value" CHARACTER VARYING(240) COMMENT '属性值' NOT NULL
);
ALTER TABLE "product_attribute" ADD CONSTRAINT "CONSTRAINT_94" PRIMARY KEY("product_id", "attr_key");
CREATE INDEX "idx_product_attribute_filter" ON "product_attribute"("attr_key" NULLS FIRST, "attr_value" NULLS FIRST, "product_id" NULLS FIRST);
CREATE TABLE "sku_attribute" COMMENT '规格级扩展属性，用于颜色、尺码等规格筛选'(
    "sku_id" BIGINT COMMENT '属性所属 SKU 编号' NOT NULL,
    "attr_key" CHARACTER VARYING(60) COMMENT '规格属性键，如颜色或尺码' NOT NULL,
    "attr_value" CHARACTER VARYING(240) COMMENT '规格属性值' NOT NULL
);
ALTER TABLE "sku_attribute" ADD CONSTRAINT "CONSTRAINT_10A" PRIMARY KEY("sku_id", "attr_key");
CREATE INDEX "idx_sku_attribute_filter" ON "sku_attribute"("attr_key" NULLS FIRST, "attr_value" NULLS FIRST, "sku_id" NULLS FIRST);
CREATE TABLE "knowledge_index_head" COMMENT 'MySQL LEGACY 兼容索引当前发布版本的指针'(
    "head_id" INTEGER COMMENT '发布指针固定记录键' NOT NULL,
    "index_version" CHARACTER VARYING(80) COMMENT '当前可用的索引版本，尚未发布时为空'
);
ALTER TABLE "knowledge_index_head" ADD CONSTRAINT "CONSTRAINT_2E" PRIMARY KEY("head_id");
CREATE TABLE "product_sku" COMMENT '可交易的商品规格，保存规格级价格和 MySQL 库存'(
    "id" BIGINT COMMENT 'SKU 编号，结算与建单按此定位规格' NOT NULL,
    "product_id" BIGINT COMMENT 'SKU 所属商品编号' NOT NULL,
    "sku_code" CHARACTER VARYING(64) COMMENT '唯一业务规格编码' NOT NULL,
    "spec" CHARACTER VARYING(160) COMMENT '规格展示名称' NOT NULL,
    "price" NUMERIC(12, 2) COMMENT '当前销售单价，单位元' NOT NULL,
    "original_price" NUMERIC(12, 2) COMMENT '展示用原价，单位元，未设置时为空',
    "stock" INTEGER COMMENT 'MySQL 可售库存，建单时扣减' NOT NULL,
    "enabled" BOOLEAN DEFAULT TRUE COMMENT '此 SKU 是否启用' NOT NULL,
    "locked_stock" INTEGER DEFAULT 0 COMMENT '已建单未支付的占用量，支付或关闭时扣减' NOT NULL,
    "redis_inventory_initialized" BOOLEAN DEFAULT FALSE COMMENT '是否已初始化 Redis 共享预扣池，用于识别库存池丢失' NOT NULL
);
ALTER TABLE "product_sku" ADD CONSTRAINT "CONSTRAINT_A" PRIMARY KEY("id");
CREATE INDEX "idx_sku_catalog_filter" ON "product_sku"("product_id" NULLS FIRST, "enabled" NULLS FIRST, "price" NULLS FIRST);
CREATE TABLE "order_event_receipt" COMMENT '订单生命周期消息的消费回执，按事件编号去重和查阅'(
    "event_id" CHARACTER VARYING(40) COMMENT '已接收的消息事件编号' NOT NULL,
    "order_id" CHARACTER VARYING(40) COMMENT '事件指向的订单编号' NOT NULL,
    "event_type" CHARACTER VARYING(32) COMMENT '订单生命周期事件类型' NOT NULL,
    "user_id" BIGINT COMMENT '事件携带的订单所属用户编号' NOT NULL,
    "pay_amount" NUMERIC(12, 2) COMMENT '事件携带的订单金额，单位元' NOT NULL,
    "occurred_at" TIMESTAMP COMMENT '事件在生产方发生的时间' NOT NULL,
    "received_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '消费回执落库时间' NOT NULL
);
ALTER TABLE "order_event_receipt" ADD CONSTRAINT "CONSTRAINT_63" PRIMARY KEY("event_id");
CREATE INDEX "idx_order_event_receipt_order" ON "order_event_receipt"("order_id" NULLS FIRST, "received_at" NULLS FIRST);
CREATE TABLE "order_message_outbox" COMMENT '订单 Outbox，持久化建单命令和生命周期消息及发送进度'(
    "event_id" CHARACTER VARYING(40) COMMENT '消息事件编号，发送到 Broker 时作为消息键' NOT NULL,
    "order_id" CHARACTER VARYING(40) COMMENT '关联订单编号，未建单的命令允许为空',
    "topic" CHARACTER VARYING(127) COMMENT 'RocketMQ 目标主题' NOT NULL,
    "event_type" CHARACTER VARYING(32) COMMENT '消息类型，同时用于 RocketMQ 标签' NOT NULL,
    "payload" CHARACTER VARYING COMMENT '待发送的业务消息正文 JSON' NOT NULL,
    "status" CHARACTER VARYING(16) DEFAULT 'PENDING' COMMENT '发送状态，PENDING、SENDING、SENT 或 CANCELLED' NOT NULL,
    "attempts" INTEGER DEFAULT 0 COMMENT '已领取发送的尝试次数' NOT NULL,
    "next_attempt_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '下次允许尝试发送的时间' NOT NULL,
    "lease_token" CHARACTER VARYING(40) COMMENT '当前发送任务的租约令牌',
    "locked_until" TIMESTAMP COMMENT '当前发送租约的到期时间',
    "broker_msg_id" CHARACTER VARYING(200) COMMENT 'Broker 确认的 MQ 消息编号',
    "last_error" CHARACTER VARYING(500) COMMENT '最近一次发送或处理失败摘要',
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT 'Outbox 记录创建时间' NOT NULL,
    "sent_at" TIMESTAMP COMMENT '持久化发送成功状态的时间',
    "trace_id" CHARACTER VARYING(40) COMMENT '关联购买意图编号，同一 trace 的同类事件唯一',
    "deliver_at_epoch_ms" BIGINT COMMENT '定时消息的 Unix 毫秒投递时间，立即投递时为空'
);
ALTER TABLE "order_message_outbox" ADD CONSTRAINT "CONSTRAINT_A1" PRIMARY KEY("event_id");
CREATE INDEX "idx_order_message_pending" ON "order_message_outbox"("status" NULLS FIRST, "next_attempt_at" NULLS FIRST, "locked_until" NULLS FIRST);
CREATE TABLE "knowledge_index_chunk" COMMENT 'MySQL LEGACY 兼容索引切片，用 JSON 保存向量与来源元数据'(
    "index_version" CHARACTER VARYING(80) COMMENT '切片归属的索引版本' NOT NULL,
    "chunk_id" CHARACTER VARYING(100) COMMENT '索引版本内的切片标识' NOT NULL,
    "document_key" CHARACTER VARYING(100) COMMENT '来源文档的稳定标识' NOT NULL,
    "content_version" CHARACTER VARYING(80) COMMENT '源文档正文版本' NOT NULL,
    "document_hash" CHARACTER VARYING(64) COMMENT '切分时的源文档摘要' NOT NULL,
    "text_hash" CHARACTER VARYING(64) COMMENT '当前切片正文摘要' NOT NULL,
    "section_title" CHARACTER VARYING(300) COMMENT '切片所在文档小节标题' NOT NULL,
    "chunk_text" CHARACTER VARYING COMMENT '供检索和引用的切片正文' NOT NULL,
    "vector_json" CHARACTER VARYING COMMENT '兼容后端使用的序列化向量 JSON' NOT NULL,
    "metadata_json" CHARACTER VARYING COMMENT '构建时记录的来源、渠道等引用属性 JSON' NOT NULL,
    "brand_code" CHARACTER VARYING(32) COMMENT '供检索过滤的品牌编码' NOT NULL,
    "document_type" CHARACTER VARYING(32) COMMENT '供检索过滤的政策类型' NOT NULL,
    "region" CHARACTER VARYING(40) COMMENT '供检索过滤的地区编码' NOT NULL
);
ALTER TABLE "knowledge_index_chunk" ADD CONSTRAINT "CONSTRAINT_B" PRIMARY KEY("index_version", "chunk_id");
CREATE INDEX "idx_knowledge_chunk_scope" ON "knowledge_index_chunk"("index_version" NULLS FIRST, "brand_code" NULLS FIRST, "document_type" NULLS FIRST, "region" NULLS FIRST);
CREATE TABLE "chat_message" COMMENT 'Agent 的用户与助手消息，后台回答复用助手消息编号作为 Turn ID'(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1) COMMENT '消息编号，后台助手回复的编号也是轮次编号' NOT NULL,
    "conversation_id" CHARACTER VARYING(40) COMMENT '消息所属会话编号' NOT NULL,
    "role" CHARACTER VARYING(20) COMMENT '消息的发言角色' NOT NULL,
    "content" CHARACTER VARYING COMMENT '页面消息正文，后台回答生成时可以为空' NOT NULL,
    "metadata" CHARACTER VARYING DEFAULT '{}' COMMENT '消息附加信息 JSON' NOT NULL,
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '消息写入时间，后台回复为占位创建时间' NOT NULL,
    "generation_status" CHARACTER VARYING(16) COMMENT '后台回答生成状态，普通消息为空',
    "reply_to_message_id" BIGINT COMMENT '本次助手回复对应的用户消息编号',
    "request_key" CHARACTER VARYING(100) COMMENT '去重键，由用户编号与客户端本次提交编号组成'
);
ALTER TABLE "chat_message" ADD CONSTRAINT "CONSTRAINT_FA" PRIMARY KEY("id");
CREATE INDEX "idx_chat_message_conversation" ON "chat_message"("conversation_id" NULLS FIRST, "id" NULLS FIRST);
CREATE INDEX "idx_chat_message_generation" ON "chat_message"("generation_status" NULLS FIRST, "conversation_id" NULLS FIRST, "id" NULLS FIRST);
CREATE TABLE "agent_turn_item" COMMENT 'Agent 一轮的排队、工具、熔断跳过及确认卡恢复等诊断事件'(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1) COMMENT '执行项明细主键' NOT NULL,
    "turn_id" BIGINT COMMENT '后台助手消息编号，即所在轮次' NOT NULL,
    "sequence_no" INTEGER COMMENT '同轮事件序号，与模型请求共用排序口径' NOT NULL,
    "step_no" INTEGER COMMENT '所属推理步骤，轮次级事件可为空',
    "item_type" CHARACTER VARYING(32) COMMENT '执行项类型，如 QUEUE_WAIT 或 TOOL_CALL' NOT NULL,
    "model_call_sequence" INTEGER COMMENT '触发该工具的模型事件序号，仅在同轮内引用',
    "candidate" CHARACTER VARYING(24) COMMENT '被熔断跳过的候选模型角色',
    "tool_call_id" CHARACTER VARYING(128) COMMENT '上游模型的工具请求标识',
    "tool_name" CHARACTER VARYING(80) COMMENT '被请求的已注册工具名称',
    "execution_count" INTEGER COMMENT '实际分发次数，校验拒绝为零，实际重试累加',
    "action_id" CHARACTER VARYING(40) COMMENT '生成或恢复的确认卡操作编号',
    "status" CHARACTER VARYING(24) COMMENT '此执行项的结果状态' NOT NULL,
    "error_code" CHARACTER VARYING(64) COMMENT '安全归类后的失败代码',
    "started_at" TIMESTAMP(6) COMMENT '事件开始时间，按 UTC 记录' NOT NULL,
    "duration_ms" BIGINT COMMENT '事件持续耗时，单位毫秒' NOT NULL
);
ALTER TABLE "agent_turn_item" ADD CONSTRAINT "CONSTRAINT_6B" PRIMARY KEY("id");
CREATE INDEX "idx_agent_turn_item_retention" ON "agent_turn_item"("started_at" NULLS FIRST, "id" NULLS FIRST);
ALTER TABLE "order_message_outbox" ADD CONSTRAINT "chk_outbox_delivery_time" CHECK(("deliver_at_epoch_ms" IS NULL)
    OR ("deliver_at_epoch_ms" > CAST(0 AS BIGINT))) NOCHECK;
ALTER TABLE "order_submit_trace" ADD CONSTRAINT "CONSTRAINT_31CA" CHECK("mode" IN('SYNC', 'ASYNC')) NOCHECK;
ALTER TABLE "order_inventory_return" ADD CONSTRAINT "CONSTRAINT_9A05" CHECK("quantity" > 0) NOCHECK;
ALTER TABLE "order_message_outbox" ADD CONSTRAINT "chk_order_message_status" CHECK("status" IN('PENDING', 'SENDING', 'SENT', 'CANCELLED')) NOCHECK;
ALTER TABLE "product_sku" ADD CONSTRAINT "chk_sku_nonnegative_stock" CHECK(("stock" >= 0)
    AND ("locked_stock" >= 0)) NOCHECK;
ALTER TABLE "order_submit_trace" ADD CONSTRAINT "CONSTRAINT_31CAD3" CHECK("status" IN('INIT', 'PROCESSING', 'SUCCESS', 'FAILED', 'EXPIRED')) NOCHECK;
ALTER TABLE "product_sku" ADD CONSTRAINT "CONSTRAINT_A71" UNIQUE NULLS DISTINCT ("sku_code");
ALTER TABLE "agent_model_call" ADD CONSTRAINT "CONSTRAINT_732" UNIQUE NULLS DISTINCT ("turn_id", "sequence_no");
ALTER TABLE "cart_item" ADD CONSTRAINT "CONSTRAINT_CADA" UNIQUE NULLS DISTINCT ("user_id", "sku_id");
ALTER TABLE "agent_model_call" ADD CONSTRAINT "CONSTRAINT_7328" UNIQUE NULLS DISTINCT ("turn_id", "step_no", "attempt_no");
ALTER TABLE "order_submit_trace" ADD CONSTRAINT "CONSTRAINT_31C" UNIQUE NULLS DISTINCT ("origin_key");
ALTER TABLE "user_memory" ADD CONSTRAINT "CONSTRAINT_EBFC" UNIQUE NULLS DISTINCT ("user_id", "memory_key");
ALTER TABLE "agent_turn_item" ADD CONSTRAINT "CONSTRAINT_6BD" UNIQUE NULLS DISTINCT ("turn_id", "sequence_no");
ALTER TABLE "user_coupon" ADD CONSTRAINT "CONSTRAINT_DB7C" UNIQUE NULLS DISTINCT ("user_id", "coupon_id");
ALTER TABLE "pending_action" ADD CONSTRAINT "CONSTRAINT_62" UNIQUE NULLS DISTINCT ("idempotency_key");
ALTER TABLE "user_coupon" ADD CONSTRAINT "CONSTRAINT_DB" FOREIGN KEY("user_id") REFERENCES "app_user"("id") NOCHECK;
ALTER TABLE "customer_order" ADD CONSTRAINT "CONSTRAINT_86" FOREIGN KEY("user_id") REFERENCES "app_user"("id") NOCHECK;
ALTER TABLE "product_attribute" ADD CONSTRAINT "CONSTRAINT_9" FOREIGN KEY("product_id") REFERENCES "product"("id") NOCHECK;
ALTER TABLE "knowledge_index_head" ADD CONSTRAINT "CONSTRAINT_2EF" FOREIGN KEY("index_version") REFERENCES "knowledge_index_version"("index_version") NOCHECK;
ALTER TABLE "product_sku" ADD CONSTRAINT "CONSTRAINT_A7" FOREIGN KEY("product_id") REFERENCES "product"("id") NOCHECK;
ALTER TABLE "user_memory" ADD CONSTRAINT "CONSTRAINT_EBF" FOREIGN KEY("user_id") REFERENCES "app_user"("id") NOCHECK;
ALTER TABLE "cart_item" ADD CONSTRAINT "CONSTRAINT_CA" FOREIGN KEY("user_id") REFERENCES "app_user"("id") NOCHECK;
ALTER TABLE "order_submit_trace" ADD CONSTRAINT "CONSTRAINT_31" FOREIGN KEY("user_id") REFERENCES "app_user"("id") NOCHECK;
ALTER TABLE "conversation_skill" ADD CONSTRAINT "CONSTRAINT_77" FOREIGN KEY("conversation_id") REFERENCES "conversation"("id") NOCHECK;
ALTER TABLE "order_message_outbox" ADD CONSTRAINT "CONSTRAINT_A1C" FOREIGN KEY("trace_id") REFERENCES "order_submit_trace"("trace_id") NOCHECK;
ALTER TABLE "cart_item" ADD CONSTRAINT "CONSTRAINT_CAD" FOREIGN KEY("sku_id") REFERENCES "product_sku"("id") NOCHECK;
ALTER TABLE "order_item" ADD CONSTRAINT "CONSTRAINT_2D1" FOREIGN KEY("product_id") REFERENCES "product"("id") NOCHECK;
ALTER TABLE "order_item" ADD CONSTRAINT "CONSTRAINT_2D11" FOREIGN KEY("sku_id") REFERENCES "product_sku"("id") NOCHECK;
ALTER TABLE "order_inventory_return" ADD CONSTRAINT "CONSTRAINT_9A0" FOREIGN KEY("sku_id") REFERENCES "product_sku"("id") NOCHECK;
ALTER TABLE "customer_order" ADD CONSTRAINT "CONSTRAINT_86D" FOREIGN KEY("submit_trace_id") REFERENCES "order_submit_trace"("trace_id") NOCHECK;
ALTER TABLE "order_item" ADD CONSTRAINT "CONSTRAINT_2D" FOREIGN KEY("order_id") REFERENCES "customer_order"("id") NOCHECK;
ALTER TABLE "conversation" ADD CONSTRAINT "CONSTRAINT_2C1" FOREIGN KEY("user_id") REFERENCES "app_user"("id") NOCHECK;
ALTER TABLE "order_inventory_return" ADD CONSTRAINT "CONSTRAINT_9A" FOREIGN KEY("order_id") REFERENCES "customer_order"("id") NOCHECK;
ALTER TABLE "logistics_event" ADD CONSTRAINT "CONSTRAINT_F1" FOREIGN KEY("order_id") REFERENCES "customer_order"("id") NOCHECK;
ALTER TABLE "user_coupon" ADD CONSTRAINT "CONSTRAINT_DB7" FOREIGN KEY("coupon_id") REFERENCES "coupon"("id") NOCHECK;
ALTER TABLE "sku_attribute" ADD CONSTRAINT "CONSTRAINT_10" FOREIGN KEY("sku_id") REFERENCES "product_sku"("id") NOCHECK;
ALTER TABLE "pending_action" ADD CONSTRAINT "CONSTRAINT_622" FOREIGN KEY("user_id") REFERENCES "app_user"("id") NOCHECK;
ALTER TABLE "after_sales" ADD CONSTRAINT "CONSTRAINT_21E8" FOREIGN KEY("user_id") REFERENCES "app_user"("id") NOCHECK;
ALTER TABLE "user_address" ADD CONSTRAINT "CONSTRAINT_16" FOREIGN KEY("user_id") REFERENCES "app_user"("id") NOCHECK;
ALTER TABLE "chat_message" ADD CONSTRAINT "CONSTRAINT_FAE" FOREIGN KEY("conversation_id") REFERENCES "conversation"("id") NOCHECK;
ALTER TABLE "knowledge_index_chunk" ADD CONSTRAINT "CONSTRAINT_B0" FOREIGN KEY("index_version") REFERENCES "knowledge_index_version"("index_version") NOCHECK;
ALTER TABLE "pending_action" ADD CONSTRAINT "CONSTRAINT_6224" FOREIGN KEY("conversation_id") REFERENCES "conversation"("id") NOCHECK;
ALTER TABLE "after_sales" ADD CONSTRAINT "CONSTRAINT_21E" FOREIGN KEY("order_id") REFERENCES "customer_order"("id") NOCHECK;
ALTER TABLE "conversation_skill" ADD CONSTRAINT "CONSTRAINT_77B" FOREIGN KEY("skill_code") REFERENCES "agent_skill"("code") NOCHECK;
