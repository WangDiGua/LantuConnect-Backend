CREATE TABLE IF NOT EXISTS `t_openai_compat_state` (
  `id` varchar(64) NOT NULL,
  `object_type` varchar(32) NOT NULL,
  `owner_type` varchar(32) NOT NULL,
  `owner_id` varchar(64) NOT NULL,
  `parent_id` varchar(64) DEFAULT NULL,
  `assistant_id` varchar(64) DEFAULT NULL,
  `role` varchar(32) DEFAULT NULL,
  `model_alias` varchar(128) DEFAULT NULL,
  `status` varchar(32) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `instructions` mediumtext,
  `content_text` mediumtext,
  `content_json` longtext,
  `output_text` mediumtext,
  `created_at` bigint NOT NULL,
  `updated_at` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_openai_compat_owner` (`object_type`, `owner_type`, `owner_id`, `created_at`),
  KEY `idx_openai_compat_parent` (`object_type`, `parent_id`, `created_at`),
  KEY `idx_openai_compat_assistant` (`assistant_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OpenAI compat persistent state';

SET @has_openai_assistant_state = (
  SELECT COUNT(*)
  FROM information_schema.tables
  WHERE table_schema = DATABASE() AND table_name = 't_openai_assistant_state'
);
SET @sql = IF(@has_openai_assistant_state > 0,
  'INSERT IGNORE INTO `t_openai_compat_state`
      (`id`, `object_type`, `owner_type`, `owner_id`, `model_alias`, `name`, `instructions`, `created_at`, `updated_at`)
   SELECT `id`, ''assistant'', `owner_type`, `owner_id`, `model_alias`, `name`, `instructions`, `created_at`, `updated_at`
   FROM `t_openai_assistant_state`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_openai_thread_state = (
  SELECT COUNT(*)
  FROM information_schema.tables
  WHERE table_schema = DATABASE() AND table_name = 't_openai_thread_state'
);
SET @sql = IF(@has_openai_thread_state > 0,
  'INSERT IGNORE INTO `t_openai_compat_state`
      (`id`, `object_type`, `owner_type`, `owner_id`, `created_at`, `updated_at`)
   SELECT `id`, ''thread'', `owner_type`, `owner_id`, `created_at`, `updated_at`
   FROM `t_openai_thread_state`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_openai_thread_message_state = (
  SELECT COUNT(*)
  FROM information_schema.tables
  WHERE table_schema = DATABASE() AND table_name = 't_openai_thread_message_state'
);
SET @sql = IF(@has_openai_thread_message_state > 0 AND @has_openai_thread_state > 0,
  'INSERT IGNORE INTO `t_openai_compat_state`
      (`id`, `object_type`, `owner_type`, `owner_id`, `parent_id`, `role`, `content_text`, `content_json`, `created_at`, `updated_at`)
   SELECT m.`id`,
          ''message'',
          COALESCE(t.`owner_type`, ''unknown''),
          COALESCE(t.`owner_id`, ''''),
          m.`thread_id`,
          m.`role`,
          m.`content_text`,
          m.`content_json`,
          m.`created_at`,
          m.`created_at`
   FROM `t_openai_thread_message_state` m
   LEFT JOIN `t_openai_thread_state` t ON t.`id` = m.`thread_id`',
  IF(@has_openai_thread_message_state > 0,
    'INSERT IGNORE INTO `t_openai_compat_state`
        (`id`, `object_type`, `owner_type`, `owner_id`, `parent_id`, `role`, `content_text`, `content_json`, `created_at`, `updated_at`)
     SELECT `id`, ''message'', ''unknown'', '''', `thread_id`, `role`, `content_text`, `content_json`, `created_at`, `created_at`
     FROM `t_openai_thread_message_state`',
    'SELECT 1'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_openai_thread_run_state = (
  SELECT COUNT(*)
  FROM information_schema.tables
  WHERE table_schema = DATABASE() AND table_name = 't_openai_thread_run_state'
);
SET @sql = IF(@has_openai_thread_run_state > 0,
  'INSERT IGNORE INTO `t_openai_compat_state`
      (`id`, `object_type`, `owner_type`, `owner_id`, `parent_id`, `assistant_id`, `model_alias`, `status`, `output_text`, `created_at`, `updated_at`)
   SELECT `id`, ''run'', `owner_type`, `owner_id`, `thread_id`, `assistant_id`, `model_alias`, `status`, `output_text`, `created_at`, `updated_at`
   FROM `t_openai_thread_run_state`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

DROP TABLE IF EXISTS `t_openai_thread_run_state`;
DROP TABLE IF EXISTS `t_openai_thread_message_state`;
DROP TABLE IF EXISTS `t_openai_thread_state`;
DROP TABLE IF EXISTS `t_openai_assistant_state`;
