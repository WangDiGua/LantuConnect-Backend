-- OpenAI 兼容态持久化：assistants / threads / runs
-- 目的：替代进程内内存状态，避免重启丢失

CREATE TABLE IF NOT EXISTS `t_openai_assistant_state` (
  `id` varchar(64) NOT NULL,
  `owner_type` varchar(32) NOT NULL,
  `owner_id` varchar(64) NOT NULL,
  `model_alias` varchar(128) NOT NULL,
  `name` varchar(255) DEFAULT NULL,
  `instructions` mediumtext,
  `created_at` bigint NOT NULL,
  `updated_at` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_openai_assistant_owner` (`owner_type`, `owner_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OpenAI兼容-Assistant持久态';

CREATE TABLE IF NOT EXISTS `t_openai_thread_state` (
  `id` varchar(64) NOT NULL,
  `owner_type` varchar(32) NOT NULL,
  `owner_id` varchar(64) NOT NULL,
  `created_at` bigint NOT NULL,
  `updated_at` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_openai_thread_owner` (`owner_type`, `owner_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OpenAI兼容-Thread持久态';

CREATE TABLE IF NOT EXISTS `t_openai_thread_message_state` (
  `id` varchar(64) NOT NULL,
  `thread_id` varchar(64) NOT NULL,
  `role` varchar(32) NOT NULL,
  `content_text` mediumtext,
  `content_json` longtext,
  `created_at` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_openai_thread_msg_thread` (`thread_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OpenAI兼容-Thread消息持久态';

CREATE TABLE IF NOT EXISTS `t_openai_thread_run_state` (
  `id` varchar(64) NOT NULL,
  `thread_id` varchar(64) NOT NULL,
  `assistant_id` varchar(64) NOT NULL,
  `owner_type` varchar(32) NOT NULL,
  `owner_id` varchar(64) NOT NULL,
  `model_alias` varchar(128) NOT NULL,
  `status` varchar(32) NOT NULL,
  `output_text` mediumtext,
  `created_at` bigint NOT NULL,
  `updated_at` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_openai_run_thread` (`thread_id`, `created_at`),
  KEY `idx_openai_run_owner` (`owner_type`, `owner_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OpenAI兼容-Run持久态';
