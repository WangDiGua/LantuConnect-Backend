CREATE TABLE IF NOT EXISTS `t_system_config` (
  `id` varchar(160) NOT NULL COMMENT 'scope:key',
  `scope` varchar(32) NOT NULL COMMENT 'system/security',
  `config_key` varchar(128) NOT NULL,
  `config_value` text NULL,
  `value_type` varchar(32) NOT NULL DEFAULT 'string',
  `label` varchar(128) NULL,
  `description` varchar(512) NULL,
  `category` varchar(64) NULL,
  `editable` tinyint(1) NOT NULL DEFAULT 1,
  `options_json` json NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_system_config_scope_key` (`scope`, `config_key`),
  KEY `idx_system_config_scope_category` (`scope`, `category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='unified system configuration';

INSERT INTO `t_system_config`
  (`id`, `scope`, `config_key`, `config_value`, `value_type`, `label`, `description`, `category`, `editable`, `options_json`, `update_time`)
SELECT
  CONCAT('system:', `key`),
  'system',
  `key`,
  `value`,
  COALESCE(NULLIF(`type`, ''), 'string'),
  NULL,
  `description`,
  `category`,
  COALESCE(`editable`, 1),
  NULL,
  COALESCE(`update_time`, NOW())
FROM `t_system_param`
ON DUPLICATE KEY UPDATE
  `config_value` = VALUES(`config_value`),
  `value_type` = VALUES(`value_type`),
  `description` = VALUES(`description`),
  `category` = VALUES(`category`),
  `editable` = VALUES(`editable`),
  `update_time` = VALUES(`update_time`);

INSERT INTO `t_system_config`
  (`id`, `scope`, `config_key`, `config_value`, `value_type`, `label`, `description`, `category`, `editable`, `options_json`, `update_time`)
SELECT
  CONCAT('security:', `key`),
  'security',
  `key`,
  `value`,
  COALESCE(NULLIF(`type`, ''), 'string'),
  `label`,
  `description`,
  `category`,
  1,
  `options`,
  NOW()
FROM `t_security_setting`
ON DUPLICATE KEY UPDATE
  `config_value` = VALUES(`config_value`),
  `value_type` = VALUES(`value_type`),
  `label` = VALUES(`label`),
  `description` = VALUES(`description`),
  `category` = VALUES(`category`),
  `options_json` = VALUES(`options_json`),
  `update_time` = VALUES(`update_time`);

DROP TABLE IF EXISTS `t_security_setting`;
DROP TABLE IF EXISTS `t_system_param`;
