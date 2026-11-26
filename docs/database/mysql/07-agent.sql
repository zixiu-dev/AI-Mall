-- Agent 会话、技能与监控｜当前 MySQL 结构（V23）
-- 由 scripts/export-database-schema.py 导出。按文件编号顺序导入空库可校验结构。
-- 应用初始化由 Flyway 执行，快照仅含结构、不含业务数据或 Flyway 历史。
SET NAMES utf8mb4;

CREATE TABLE `conversation` (
  `id` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '会话编号，对应监控页的 Thread ID',
  `user_id` bigint NOT NULL COMMENT '会话所属用户编号',
  `title` varchar(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '新会话' COMMENT '会话展示标题',
  `summary` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin COMMENT '较早轮次的滚动摘要',
  `summarized_until_id` bigint DEFAULT NULL COMMENT '已包含在摘要中的最后一条消息编号',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '会话创建时间',
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新或新增消息的时间',
  PRIMARY KEY (`id`),
  KEY `fk_conversation_user_id` (`user_id`),
  CONSTRAINT `fk_conversation_user_id` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='Agent 会话主表，一个会话可包含多轮问答';

CREATE TABLE `chat_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '消息编号，后台助手回复的编号也是轮次编号',
  `conversation_id` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '消息所属会话编号',
  `role` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '消息的发言角色',
  `content` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '页面消息正文，后台回答生成时可以为空',
  `metadata` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT (_utf8mb4'{}') COMMENT '消息附加信息 JSON',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '消息写入时间，后台回复为占位创建时间',
  `generation_status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '后台回答生成状态，普通消息为空',
  `reply_to_message_id` bigint DEFAULT NULL COMMENT '本次助手回复对应的用户消息编号',
  `request_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '去重键，由用户编号与客户端本次提交编号组成',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_chat_message_request_key` (`request_key`),
  KEY `idx_chat_message_conversation` (`conversation_id`,`id`),
  KEY `idx_chat_message_generation` (`generation_status`,`conversation_id`,`id`),
  CONSTRAINT `fk_chat_message_conversation_id` FOREIGN KEY (`conversation_id`) REFERENCES `conversation` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='Agent 的用户与助手消息，后台回答复用助手消息编号作为 Turn ID';

CREATE TABLE `agent_scope_state` (
  `user_id` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'Agent 运行状态所属用户的字符串标识',
  `session_id` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'Agent 运行状态所属会话标识',
  `state_key` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '运行状态键，当前使用 spring-ai-v1 存放模型消息',
  `payload` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '序列化的模型上下文及工具消息',
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '运行状态最近写入时间',
  PRIMARY KEY (`user_id`,`session_id`,`state_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='Agent 模型运行状态，当前 Spring AI 消息沿用此历史表名';

CREATE TABLE `user_memory` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户偏好记录编号',
  `user_id` bigint NOT NULL COMMENT '偏好所属用户编号',
  `memory_key` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '偏好键，同一用户下唯一',
  `memory_value` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '用户授权记录的偏好内容',
  `source_conversation_id` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '形成这条偏好的来源会话编号',
  `version` int NOT NULL DEFAULT '0' COMMENT '偏好记录版本',
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '偏好最近更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `user_id` (`user_id`,`memory_key`),
  CONSTRAINT `fk_user_memory_user_id` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='用户明确要求长期保留的购物偏好';

CREATE TABLE `agent_skill` (
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '技能的稳定标识，用于 load_skill 和校验',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '技能展示名称',
  `description` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '何时适合加载此技能的简短说明',
  `manual` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '技能办理手册正文',
  `tool_names` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '技能对应的工具名称列表 JSON',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '技能是否启用',
  PRIMARY KEY (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='Agent 可按需加载的业务技能手册和关联工具';

CREATE TABLE `conversation_skill` (
  `conversation_id` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '加载技能的会话编号',
  `skill_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '被加载的技能编码',
  `loaded_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '技能加载记录的写入时间',
  PRIMARY KEY (`conversation_id`,`skill_code`),
  KEY `fk_conversation_skill_skill_code` (`skill_code`),
  CONSTRAINT `fk_conversation_skill_conversation_id` FOREIGN KEY (`conversation_id`) REFERENCES `conversation` (`id`),
  CONSTRAINT `fk_conversation_skill_skill_code` FOREIGN KEY (`skill_code`) REFERENCES `agent_skill` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='会话与已加载 Agent 技能的关联';

CREATE TABLE `pending_action` (
  `id` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '待确认操作编号',
  `idempotency_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '创建待确认操作时使用的幂等键',
  `user_id` bigint NOT NULL COMMENT '有权确认的用户编号',
  `conversation_id` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '创建该操作的会话编号',
  `tool_name` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '待确认的写工具名称',
  `arguments` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '待确认操作的规范化参数 JSON',
  `arguments_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '参数摘要，用于识别操作参数',
  `summary` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '确认卡向用户展示的操作摘要',
  `status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '待确认操作的处理状态',
  `result` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin COMMENT '确认执行或失败后的结果 JSON',
  `version` int NOT NULL DEFAULT '0' COMMENT '用于确认状态更新的并发版本号',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '确认操作的创建时间',
  `decided_at` datetime(6) DEFAULT NULL COMMENT '用户作出确认或拒绝决定的时间',
  `executed_at` datetime(6) DEFAULT NULL COMMENT '记录实际执行结果的时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idempotency_key` (`idempotency_key`),
  KEY `fk_pending_action_user_id` (`user_id`),
  KEY `idx_pending_action_conversation` (`conversation_id`,`status`,`created_at`),
  CONSTRAINT `fk_pending_action_conversation_id` FOREIGN KEY (`conversation_id`) REFERENCES `conversation` (`id`),
  CONSTRAINT `fk_pending_action_user_id` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='Agent 待用户确认的操作，保存可信参数及确认执行结果';

CREATE TABLE `agent_model_call` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '模型调用明细主键',
  `turn_id` bigint NOT NULL COMMENT '后台助手消息编号，即所在轮次',
  `sequence_no` int NOT NULL COMMENT '同轮事件序号，与执行项共用排序口径',
  `step_no` int NOT NULL COMMENT 'Agent 循环中的推理步骤编号',
  `attempt_no` int NOT NULL COMMENT '同一步内实际模型请求的尝试编号',
  `candidate` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '模型候选角色，如 primary 或 bailian',
  `model` varchar(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '实际请求的模型标识',
  `status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '此次模型请求的结果状态',
  `error_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '安全归类后的模型失败代码',
  `started_at` datetime(6) NOT NULL COMMENT '调用开始时间，按 UTC 记录',
  `duration_ms` bigint NOT NULL COMMENT '调用总耗时，单位毫秒',
  `first_text_ms` bigint DEFAULT NULL COMMENT '首段文本耗时，未收到文字时为空',
  `input_tokens` int DEFAULT NULL COMMENT '上游回传的输入 Token，未回传时为空',
  `output_tokens` int DEFAULT NULL COMMENT '上游回传的输出 Token，未回传时为空',
  PRIMARY KEY (`id`),
  UNIQUE KEY `turn_id` (`turn_id`,`sequence_no`),
  UNIQUE KEY `turn_id_2` (`turn_id`,`step_no`,`attempt_no`),
  KEY `idx_agent_model_call_retention` (`started_at`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='Agent 每次真实模型请求的诊断明细';

CREATE TABLE `agent_turn_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '执行项明细主键',
  `turn_id` bigint NOT NULL COMMENT '后台助手消息编号，即所在轮次',
  `sequence_no` int NOT NULL COMMENT '同轮事件序号，与模型请求共用排序口径',
  `step_no` int DEFAULT NULL COMMENT '所属推理步骤，轮次级事件可为空',
  `item_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '执行项类型，如 QUEUE_WAIT 或 TOOL_CALL',
  `model_call_sequence` int DEFAULT NULL COMMENT '触发该工具的模型事件序号，仅在同轮内引用',
  `candidate` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '被熔断跳过的候选模型角色',
  `tool_call_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '上游模型的工具请求标识',
  `tool_name` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '被请求的已注册工具名称',
  `execution_count` int DEFAULT NULL COMMENT '实际分发次数，校验拒绝为零，实际重试累加',
  `action_id` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '生成或恢复的确认卡操作编号',
  `status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '此执行项的结果状态',
  `error_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '安全归类后的失败代码',
  `started_at` datetime(6) NOT NULL COMMENT '事件开始时间，按 UTC 记录',
  `duration_ms` bigint NOT NULL COMMENT '事件持续耗时，单位毫秒',
  PRIMARY KEY (`id`),
  UNIQUE KEY `turn_id` (`turn_id`,`sequence_no`),
  KEY `idx_agent_turn_item_retention` (`started_at`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='Agent 一轮的排队、工具、熔断跳过及确认卡恢复等诊断事件';
