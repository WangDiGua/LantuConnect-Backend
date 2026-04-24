SET @has_column = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 't_call_log' AND column_name = 'usage_id'
);
SET @sql = IF(@has_column > 0, 'SELECT 1',
  'ALTER TABLE `t_call_log` ADD COLUMN `usage_id` bigint NOT NULL AUTO_INCREMENT UNIQUE COMMENT ''使用记录序号'' FIRST'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_column = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 't_call_log' AND column_name = 'display_name'
);
SET @sql = IF(@has_column > 0, 'SELECT 1',
  'ALTER TABLE `t_call_log` ADD COLUMN `display_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT ''资源显示名称'' AFTER `agent_name`'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_column = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 't_call_log' AND column_name = 'action'
);
SET @sql = IF(@has_column > 0, 'SELECT 1',
  'ALTER TABLE `t_call_log` ADD COLUMN `action` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT ''invoke'' COMMENT ''用户动作'' AFTER `method`'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_column = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 't_call_log' AND column_name = 'input_preview'
);
SET @sql = IF(@has_column > 0, 'SELECT 1',
  'ALTER TABLE `t_call_log` ADD COLUMN `input_preview` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT ''输入摘要'' AFTER `action`'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_column = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 't_call_log' AND column_name = 'output_preview'
);
SET @sql = IF(@has_column > 0, 'SELECT 1',
  'ALTER TABLE `t_call_log` ADD COLUMN `output_preview` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT ''输出摘要'' AFTER `input_preview`'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

DROP TEMPORARY TABLE IF EXISTS `tmp_v67_usage_match`;
CREATE TEMPORARY TABLE `tmp_v67_usage_match` (
  `call_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `usage_id` bigint NOT NULL,
  PRIMARY KEY (`call_id`),
  KEY `idx_tmp_v67_usage_id` (`usage_id`)
) ENGINE=MEMORY;

TRUNCATE TABLE `tmp_v67_usage_match`;

SET @has_usage_record = (
  SELECT COUNT(*)
  FROM information_schema.tables
  WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE' AND table_name = 't_usage_record'
);

SET @sql = IF(@has_usage_record > 0,
  'INSERT INTO `tmp_v67_usage_match` (`call_id`, `usage_id`)
   SELECT cl.`id`, MAX(ur.`id`)
   FROM `t_call_log` cl
   JOIN `t_usage_record` ur
     ON cl.`user_id` = CAST(ur.`user_id` AS CHAR) COLLATE utf8mb4_general_ci
    AND cl.`resource_type` = ur.`type` COLLATE utf8mb4_general_ci
    AND cl.`agent_id` = CAST(ur.`resource_id` AS CHAR) COLLATE utf8mb4_general_ci
    AND cl.`latency_ms` = ur.`latency_ms`
    AND cl.`status` = ur.`status` COLLATE utf8mb4_general_ci
    AND ABS(TIMESTAMPDIFF(SECOND, cl.`create_time`, ur.`create_time`)) <= 2
   GROUP BY cl.`id`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(@has_usage_record > 0,
  'UPDATE `t_call_log` cl
   JOIN `tmp_v67_usage_match` m ON m.`call_id` = cl.`id`
   JOIN `t_usage_record` ur ON ur.`id` = m.`usage_id`
   SET cl.`display_name` = ur.`display_name`,
       cl.`action` = ur.`action`,
       cl.`input_preview` = ur.`input_preview`,
       cl.`output_preview` = ur.`output_preview`,
       cl.`resource_type` = COALESCE(NULLIF(cl.`resource_type`, ''''), ur.`type` COLLATE utf8mb4_general_ci),
       cl.`agent_name` = COALESCE(NULLIF(cl.`agent_name`, ''''), ur.`agent_name` COLLATE utf8mb4_general_ci)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(@has_usage_record > 0,
  'INSERT IGNORE INTO `t_call_log`
      (`id`, `trace_id`, `agent_id`, `agent_name`, `display_name`, `resource_type`, `user_id`,
       `method`, `action`, `input_preview`, `output_preview`, `status`, `status_code`, `latency_ms`,
       `error_message`, `ip`, `create_time`)
   SELECT CONCAT(''usage-'', ur.`id`),
          CONCAT(''usage-'', ur.`id`),
          COALESCE(CAST(ur.`resource_id` AS CHAR), NULLIF(ur.`agent_name` COLLATE utf8mb4_general_ci, ''''), ''0''),
          ur.`agent_name`,
          ur.`display_name`,
          ur.`type`,
          CAST(ur.`user_id` AS CHAR),
          CONCAT(''usage/'', ur.`action`),
          ur.`action`,
          ur.`input_preview`,
          ur.`output_preview`,
          ur.`status`,
          CASE WHEN ur.`status` = ''success'' THEN 200 ELSE 500 END,
          COALESCE(ur.`latency_ms`, 0),
          NULL,
          ''0.0.0.0'',
          ur.`create_time`
   FROM `t_usage_record` ur
   LEFT JOIN `tmp_v67_usage_match` m ON m.`usage_id` = ur.`id`
   WHERE m.`usage_id` IS NULL',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

DROP TEMPORARY TABLE IF EXISTS `tmp_v67_usage_match`;
DROP TABLE IF EXISTS `t_usage_record`;

SET @has_column = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 't_resource' AND column_name = 'is_public'
);
SET @sql = IF(@has_column > 0, 'SELECT 1',
  'ALTER TABLE `t_resource` ADD COLUMN `is_public` tinyint(1) DEFAULT NULL COMMENT ''是否公开'' AFTER `view_count`'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_column = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 't_resource' AND column_name = 'service_detail_md'
);
SET @sql = IF(@has_column > 0, 'SELECT 1',
  'ALTER TABLE `t_resource` ADD COLUMN `service_detail_md` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT ''服务详情Markdown'' AFTER `is_public`'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_column = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 't_resource' AND column_name = 'detail_json'
);
SET @sql = IF(@has_column > 0, 'SELECT 1',
  'ALTER TABLE `t_resource` ADD COLUMN `detail_json` json DEFAULT NULL COMMENT ''资源类型扩展配置'' AFTER `service_detail_md`'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_resource_detail = (
  SELECT COUNT(*)
  FROM information_schema.tables
  WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE' AND table_name = 't_resource_detail'
);

SET @sql = IF(@has_resource_detail > 0,
  'UPDATE `t_resource` r
   JOIN `t_resource_detail` d ON d.`resource_id` = r.`id`
   SET r.`is_public` = d.`is_public`,
       r.`service_detail_md` = d.`service_detail_md`,
       r.`detail_json` = d.`detail_json`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(@has_resource_detail > 0,
  'DROP TABLE `t_resource_detail`',
  'DROP VIEW IF EXISTS `t_resource_detail`'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_sensitive_action_audit = (
  SELECT COUNT(*)
  FROM information_schema.tables
  WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE' AND table_name = 't_sensitive_action_audit'
);

SET @sql = IF(@has_sensitive_action_audit > 0,
  'INSERT IGNORE INTO `t_audit_log`
      (`id`, `user_id`, `username`, `action`, `resource`, `resource_id`, `details`, `ip`, `user_agent`, `result`, `create_time`)
   SELECT CONCAT(''sensitive-'', `id`),
          CAST(`user_id` AS CHAR),
          CAST(`user_id` AS CHAR),
          `action_type`,
          ''api_key'',
          `target_id`,
          JSON_OBJECT(''sensitive'', true, ''actionType'', `action_type`, ''failReason'', `fail_reason`),
          COALESCE(`client_ip`, ''0.0.0.0''),
          NULL,
          CASE WHEN `success` = 1 THEN ''success'' ELSE ''failure'' END,
          `created_at`
   FROM `t_sensitive_action_audit`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

DROP TABLE IF EXISTS `t_sensitive_action_audit`;
