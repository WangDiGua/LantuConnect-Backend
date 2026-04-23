CREATE TABLE IF NOT EXISTS `t_resource_detail` (
  `resource_id` bigint NOT NULL,
  `resource_type` varchar(32) NOT NULL,
  `is_public` tinyint(1) NULL,
  `service_detail_md` mediumtext NULL,
  `detail_json` json NULL,
  `source_table` varchar(64) NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`resource_id`),
  KEY `idx_resource_detail_type` (`resource_type`),
  KEY `idx_resource_detail_public` (`is_public`),
  CONSTRAINT `fk_resource_detail_resource`
    FOREIGN KEY (`resource_id`) REFERENCES `t_resource` (`id`)
    ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='unified resource detail shadow table';

INSERT INTO `t_resource_detail` (`resource_id`, `resource_type`, `is_public`, `service_detail_md`, `detail_json`, `source_table`, `update_time`)
SELECT
  e.`resource_id`,
  COALESCE(r.`resource_type`, 'agent'),
  e.`is_public`,
  e.`service_detail_md`,
  JSON_OBJECT(
    'agent_type', e.`agent_type`,
    'mode', e.`mode`,
    'spec_json', e.`spec_json`,
    'hidden', e.`hidden`,
    'max_concurrency', e.`max_concurrency`,
    'max_steps', e.`max_steps`,
    'temperature', e.`temperature`,
    'system_prompt', e.`system_prompt`,
    'featured', e.`featured`,
    'rating_avg', e.`rating_avg`,
    'rating_count', e.`rating_count`,
    'registration_protocol', e.`registration_protocol`,
    'upstream_endpoint', e.`upstream_endpoint`,
    'upstream_agent_id', e.`upstream_agent_id`,
    'credential_ref', e.`credential_ref`,
    'transform_profile', e.`transform_profile`,
    'model_alias', e.`model_alias`,
    'enabled', e.`enabled`
  ),
  't_resource_agent_ext',
  NOW()
FROM `t_resource_agent_ext` e
LEFT JOIN `t_resource` r ON r.`id` = e.`resource_id`
ON DUPLICATE KEY UPDATE
  `resource_type` = VALUES(`resource_type`),
  `is_public` = VALUES(`is_public`),
  `service_detail_md` = VALUES(`service_detail_md`),
  `detail_json` = VALUES(`detail_json`),
  `source_table` = VALUES(`source_table`),
  `update_time` = NOW();

INSERT INTO `t_resource_detail` (`resource_id`, `resource_type`, `is_public`, `service_detail_md`, `detail_json`, `source_table`, `update_time`)
SELECT
  e.`resource_id`,
  COALESCE(r.`resource_type`, 'skill'),
  e.`is_public`,
  e.`service_detail_md`,
  JSON_OBJECT(
    'skill_type', e.`skill_type`,
    'execution_mode', e.`execution_mode`,
    'manifest_json', e.`manifest_json`,
    'entry_doc', e.`entry_doc`,
    'mode', e.`mode`,
    'parent_resource_id', e.`parent_resource_id`,
    'display_template', e.`display_template`,
    'spec_json', e.`spec_json`,
    'parameters_schema', e.`parameters_schema`,
    'max_concurrency', e.`max_concurrency`,
    'pack_validation_status', e.`pack_validation_status`,
    'pack_validated_at', e.`pack_validated_at`,
    'pack_validation_message', e.`pack_validation_message`,
    'skill_root_path', e.`skill_root_path`,
    'hosted_system_prompt', e.`hosted_system_prompt`,
    'hosted_user_template', e.`hosted_user_template`,
    'hosted_default_model', e.`hosted_default_model`,
    'hosted_output_schema', e.`hosted_output_schema`,
    'hosted_temperature', e.`hosted_temperature`
  ),
  't_resource_skill_ext',
  NOW()
FROM `t_resource_skill_ext` e
LEFT JOIN `t_resource` r ON r.`id` = e.`resource_id`
ON DUPLICATE KEY UPDATE
  `resource_type` = VALUES(`resource_type`),
  `is_public` = VALUES(`is_public`),
  `service_detail_md` = VALUES(`service_detail_md`),
  `detail_json` = VALUES(`detail_json`),
  `source_table` = VALUES(`source_table`),
  `update_time` = NOW();

INSERT INTO `t_resource_detail` (`resource_id`, `resource_type`, `is_public`, `service_detail_md`, `detail_json`, `source_table`, `update_time`)
SELECT
  e.`resource_id`,
  COALESCE(r.`resource_type`, 'mcp'),
  NULL,
  e.`service_detail_md`,
  JSON_OBJECT(
    'endpoint', e.`endpoint`,
    'protocol', e.`protocol`,
    'auth_type', e.`auth_type`,
    'auth_config', e.`auth_config`
  ),
  't_resource_mcp_ext',
  NOW()
FROM `t_resource_mcp_ext` e
LEFT JOIN `t_resource` r ON r.`id` = e.`resource_id`
ON DUPLICATE KEY UPDATE
  `resource_type` = VALUES(`resource_type`),
  `is_public` = VALUES(`is_public`),
  `service_detail_md` = VALUES(`service_detail_md`),
  `detail_json` = VALUES(`detail_json`),
  `source_table` = VALUES(`source_table`),
  `update_time` = NOW();

INSERT INTO `t_resource_detail` (`resource_id`, `resource_type`, `is_public`, `service_detail_md`, `detail_json`, `source_table`, `update_time`)
SELECT
  e.`resource_id`,
  COALESCE(r.`resource_type`, 'app'),
  e.`is_public`,
  e.`service_detail_md`,
  JSON_OBJECT(
    'app_url', e.`app_url`,
    'embed_type', e.`embed_type`,
    'icon', e.`icon`,
    'screenshots', e.`screenshots`,
    'agent_exposure', e.`agent_exposure`,
    'agent_delivery_mode', e.`agent_delivery_mode`
  ),
  't_resource_app_ext',
  NOW()
FROM `t_resource_app_ext` e
LEFT JOIN `t_resource` r ON r.`id` = e.`resource_id`
ON DUPLICATE KEY UPDATE
  `resource_type` = VALUES(`resource_type`),
  `is_public` = VALUES(`is_public`),
  `service_detail_md` = VALUES(`service_detail_md`),
  `detail_json` = VALUES(`detail_json`),
  `source_table` = VALUES(`source_table`),
  `update_time` = NOW();

INSERT INTO `t_resource_detail` (`resource_id`, `resource_type`, `is_public`, `service_detail_md`, `detail_json`, `source_table`, `update_time`)
SELECT
  e.`resource_id`,
  COALESCE(r.`resource_type`, 'dataset'),
  e.`is_public`,
  e.`service_detail_md`,
  JSON_OBJECT(
    'data_type', e.`data_type`,
    'format', e.`format`,
    'record_count', e.`record_count`,
    'file_size', e.`file_size`,
    'tags', e.`tags`
  ),
  't_resource_dataset_ext',
  NOW()
FROM `t_resource_dataset_ext` e
LEFT JOIN `t_resource` r ON r.`id` = e.`resource_id`
ON DUPLICATE KEY UPDATE
  `resource_type` = VALUES(`resource_type`),
  `is_public` = VALUES(`is_public`),
  `service_detail_md` = VALUES(`service_detail_md`),
  `detail_json` = VALUES(`detail_json`),
  `source_table` = VALUES(`source_table`),
  `update_time` = NOW();

DROP TRIGGER IF EXISTS `trg_resource_agent_ext_ai`;
DROP TRIGGER IF EXISTS `trg_resource_agent_ext_au`;
DROP TRIGGER IF EXISTS `trg_resource_agent_ext_ad`;
DROP TRIGGER IF EXISTS `trg_resource_skill_ext_ai`;
DROP TRIGGER IF EXISTS `trg_resource_skill_ext_au`;
DROP TRIGGER IF EXISTS `trg_resource_skill_ext_ad`;
DROP TRIGGER IF EXISTS `trg_resource_mcp_ext_ai`;
DROP TRIGGER IF EXISTS `trg_resource_mcp_ext_au`;
DROP TRIGGER IF EXISTS `trg_resource_mcp_ext_ad`;
DROP TRIGGER IF EXISTS `trg_resource_app_ext_ai`;
DROP TRIGGER IF EXISTS `trg_resource_app_ext_au`;
DROP TRIGGER IF EXISTS `trg_resource_app_ext_ad`;
DROP TRIGGER IF EXISTS `trg_resource_dataset_ext_ai`;
DROP TRIGGER IF EXISTS `trg_resource_dataset_ext_au`;
DROP TRIGGER IF EXISTS `trg_resource_dataset_ext_ad`;

DELIMITER $$

CREATE TRIGGER `trg_resource_agent_ext_ai` AFTER INSERT ON `t_resource_agent_ext` FOR EACH ROW
BEGIN
  INSERT INTO `t_resource_detail` (`resource_id`, `resource_type`, `is_public`, `service_detail_md`, `detail_json`, `source_table`, `update_time`)
  SELECT NEW.`resource_id`, COALESCE(r.`resource_type`, 'agent'), NEW.`is_public`, NEW.`service_detail_md`,
    JSON_OBJECT('agent_type', NEW.`agent_type`, 'mode', NEW.`mode`, 'spec_json', NEW.`spec_json`, 'hidden', NEW.`hidden`, 'max_concurrency', NEW.`max_concurrency`, 'max_steps', NEW.`max_steps`, 'temperature', NEW.`temperature`, 'system_prompt', NEW.`system_prompt`, 'featured', NEW.`featured`, 'rating_avg', NEW.`rating_avg`, 'rating_count', NEW.`rating_count`, 'registration_protocol', NEW.`registration_protocol`, 'upstream_endpoint', NEW.`upstream_endpoint`, 'upstream_agent_id', NEW.`upstream_agent_id`, 'credential_ref', NEW.`credential_ref`, 'transform_profile', NEW.`transform_profile`, 'model_alias', NEW.`model_alias`, 'enabled', NEW.`enabled`),
    't_resource_agent_ext', NOW()
  FROM `t_resource` r WHERE r.`id` = NEW.`resource_id`
  ON DUPLICATE KEY UPDATE `resource_type` = VALUES(`resource_type`), `is_public` = VALUES(`is_public`), `service_detail_md` = VALUES(`service_detail_md`), `detail_json` = VALUES(`detail_json`), `source_table` = VALUES(`source_table`), `update_time` = NOW();
END$$

CREATE TRIGGER `trg_resource_agent_ext_au` AFTER UPDATE ON `t_resource_agent_ext` FOR EACH ROW
BEGIN
  INSERT INTO `t_resource_detail` (`resource_id`, `resource_type`, `is_public`, `service_detail_md`, `detail_json`, `source_table`, `update_time`)
  SELECT NEW.`resource_id`, COALESCE(r.`resource_type`, 'agent'), NEW.`is_public`, NEW.`service_detail_md`,
    JSON_OBJECT('agent_type', NEW.`agent_type`, 'mode', NEW.`mode`, 'spec_json', NEW.`spec_json`, 'hidden', NEW.`hidden`, 'max_concurrency', NEW.`max_concurrency`, 'max_steps', NEW.`max_steps`, 'temperature', NEW.`temperature`, 'system_prompt', NEW.`system_prompt`, 'featured', NEW.`featured`, 'rating_avg', NEW.`rating_avg`, 'rating_count', NEW.`rating_count`, 'registration_protocol', NEW.`registration_protocol`, 'upstream_endpoint', NEW.`upstream_endpoint`, 'upstream_agent_id', NEW.`upstream_agent_id`, 'credential_ref', NEW.`credential_ref`, 'transform_profile', NEW.`transform_profile`, 'model_alias', NEW.`model_alias`, 'enabled', NEW.`enabled`),
    't_resource_agent_ext', NOW()
  FROM `t_resource` r WHERE r.`id` = NEW.`resource_id`
  ON DUPLICATE KEY UPDATE `resource_type` = VALUES(`resource_type`), `is_public` = VALUES(`is_public`), `service_detail_md` = VALUES(`service_detail_md`), `detail_json` = VALUES(`detail_json`), `source_table` = VALUES(`source_table`), `update_time` = NOW();
END$$

CREATE TRIGGER `trg_resource_agent_ext_ad` AFTER DELETE ON `t_resource_agent_ext` FOR EACH ROW
BEGIN
  DELETE FROM `t_resource_detail` WHERE `resource_id` = OLD.`resource_id` AND `source_table` = 't_resource_agent_ext';
END$$

CREATE TRIGGER `trg_resource_skill_ext_ai` AFTER INSERT ON `t_resource_skill_ext` FOR EACH ROW
BEGIN
  INSERT INTO `t_resource_detail` (`resource_id`, `resource_type`, `is_public`, `service_detail_md`, `detail_json`, `source_table`, `update_time`)
  SELECT NEW.`resource_id`, COALESCE(r.`resource_type`, 'skill'), NEW.`is_public`, NEW.`service_detail_md`,
    JSON_OBJECT('skill_type', NEW.`skill_type`, 'execution_mode', NEW.`execution_mode`, 'manifest_json', NEW.`manifest_json`, 'entry_doc', NEW.`entry_doc`, 'mode', NEW.`mode`, 'parent_resource_id', NEW.`parent_resource_id`, 'display_template', NEW.`display_template`, 'spec_json', NEW.`spec_json`, 'parameters_schema', NEW.`parameters_schema`, 'max_concurrency', NEW.`max_concurrency`, 'pack_validation_status', NEW.`pack_validation_status`, 'pack_validated_at', NEW.`pack_validated_at`, 'pack_validation_message', NEW.`pack_validation_message`, 'skill_root_path', NEW.`skill_root_path`, 'hosted_system_prompt', NEW.`hosted_system_prompt`, 'hosted_user_template', NEW.`hosted_user_template`, 'hosted_default_model', NEW.`hosted_default_model`, 'hosted_output_schema', NEW.`hosted_output_schema`, 'hosted_temperature', NEW.`hosted_temperature`),
    't_resource_skill_ext', NOW()
  FROM `t_resource` r WHERE r.`id` = NEW.`resource_id`
  ON DUPLICATE KEY UPDATE `resource_type` = VALUES(`resource_type`), `is_public` = VALUES(`is_public`), `service_detail_md` = VALUES(`service_detail_md`), `detail_json` = VALUES(`detail_json`), `source_table` = VALUES(`source_table`), `update_time` = NOW();
END$$

CREATE TRIGGER `trg_resource_skill_ext_au` AFTER UPDATE ON `t_resource_skill_ext` FOR EACH ROW
BEGIN
  INSERT INTO `t_resource_detail` (`resource_id`, `resource_type`, `is_public`, `service_detail_md`, `detail_json`, `source_table`, `update_time`)
  SELECT NEW.`resource_id`, COALESCE(r.`resource_type`, 'skill'), NEW.`is_public`, NEW.`service_detail_md`,
    JSON_OBJECT('skill_type', NEW.`skill_type`, 'execution_mode', NEW.`execution_mode`, 'manifest_json', NEW.`manifest_json`, 'entry_doc', NEW.`entry_doc`, 'mode', NEW.`mode`, 'parent_resource_id', NEW.`parent_resource_id`, 'display_template', NEW.`display_template`, 'spec_json', NEW.`spec_json`, 'parameters_schema', NEW.`parameters_schema`, 'max_concurrency', NEW.`max_concurrency`, 'pack_validation_status', NEW.`pack_validation_status`, 'pack_validated_at', NEW.`pack_validated_at`, 'pack_validation_message', NEW.`pack_validation_message`, 'skill_root_path', NEW.`skill_root_path`, 'hosted_system_prompt', NEW.`hosted_system_prompt`, 'hosted_user_template', NEW.`hosted_user_template`, 'hosted_default_model', NEW.`hosted_default_model`, 'hosted_output_schema', NEW.`hosted_output_schema`, 'hosted_temperature', NEW.`hosted_temperature`),
    't_resource_skill_ext', NOW()
  FROM `t_resource` r WHERE r.`id` = NEW.`resource_id`
  ON DUPLICATE KEY UPDATE `resource_type` = VALUES(`resource_type`), `is_public` = VALUES(`is_public`), `service_detail_md` = VALUES(`service_detail_md`), `detail_json` = VALUES(`detail_json`), `source_table` = VALUES(`source_table`), `update_time` = NOW();
END$$

CREATE TRIGGER `trg_resource_skill_ext_ad` AFTER DELETE ON `t_resource_skill_ext` FOR EACH ROW
BEGIN
  DELETE FROM `t_resource_detail` WHERE `resource_id` = OLD.`resource_id` AND `source_table` = 't_resource_skill_ext';
END$$

CREATE TRIGGER `trg_resource_mcp_ext_ai` AFTER INSERT ON `t_resource_mcp_ext` FOR EACH ROW
BEGIN
  INSERT INTO `t_resource_detail` (`resource_id`, `resource_type`, `is_public`, `service_detail_md`, `detail_json`, `source_table`, `update_time`)
  SELECT NEW.`resource_id`, COALESCE(r.`resource_type`, 'mcp'), NULL, NEW.`service_detail_md`,
    JSON_OBJECT('endpoint', NEW.`endpoint`, 'protocol', NEW.`protocol`, 'auth_type', NEW.`auth_type`, 'auth_config', NEW.`auth_config`),
    't_resource_mcp_ext', NOW()
  FROM `t_resource` r WHERE r.`id` = NEW.`resource_id`
  ON DUPLICATE KEY UPDATE `resource_type` = VALUES(`resource_type`), `is_public` = VALUES(`is_public`), `service_detail_md` = VALUES(`service_detail_md`), `detail_json` = VALUES(`detail_json`), `source_table` = VALUES(`source_table`), `update_time` = NOW();
END$$

CREATE TRIGGER `trg_resource_mcp_ext_au` AFTER UPDATE ON `t_resource_mcp_ext` FOR EACH ROW
BEGIN
  INSERT INTO `t_resource_detail` (`resource_id`, `resource_type`, `is_public`, `service_detail_md`, `detail_json`, `source_table`, `update_time`)
  SELECT NEW.`resource_id`, COALESCE(r.`resource_type`, 'mcp'), NULL, NEW.`service_detail_md`,
    JSON_OBJECT('endpoint', NEW.`endpoint`, 'protocol', NEW.`protocol`, 'auth_type', NEW.`auth_type`, 'auth_config', NEW.`auth_config`),
    't_resource_mcp_ext', NOW()
  FROM `t_resource` r WHERE r.`id` = NEW.`resource_id`
  ON DUPLICATE KEY UPDATE `resource_type` = VALUES(`resource_type`), `is_public` = VALUES(`is_public`), `service_detail_md` = VALUES(`service_detail_md`), `detail_json` = VALUES(`detail_json`), `source_table` = VALUES(`source_table`), `update_time` = NOW();
END$$

CREATE TRIGGER `trg_resource_mcp_ext_ad` AFTER DELETE ON `t_resource_mcp_ext` FOR EACH ROW
BEGIN
  DELETE FROM `t_resource_detail` WHERE `resource_id` = OLD.`resource_id` AND `source_table` = 't_resource_mcp_ext';
END$$

CREATE TRIGGER `trg_resource_app_ext_ai` AFTER INSERT ON `t_resource_app_ext` FOR EACH ROW
BEGIN
  INSERT INTO `t_resource_detail` (`resource_id`, `resource_type`, `is_public`, `service_detail_md`, `detail_json`, `source_table`, `update_time`)
  SELECT NEW.`resource_id`, COALESCE(r.`resource_type`, 'app'), NEW.`is_public`, NEW.`service_detail_md`,
    JSON_OBJECT('app_url', NEW.`app_url`, 'embed_type', NEW.`embed_type`, 'icon', NEW.`icon`, 'screenshots', NEW.`screenshots`, 'agent_exposure', NEW.`agent_exposure`, 'agent_delivery_mode', NEW.`agent_delivery_mode`),
    't_resource_app_ext', NOW()
  FROM `t_resource` r WHERE r.`id` = NEW.`resource_id`
  ON DUPLICATE KEY UPDATE `resource_type` = VALUES(`resource_type`), `is_public` = VALUES(`is_public`), `service_detail_md` = VALUES(`service_detail_md`), `detail_json` = VALUES(`detail_json`), `source_table` = VALUES(`source_table`), `update_time` = NOW();
END$$

CREATE TRIGGER `trg_resource_app_ext_au` AFTER UPDATE ON `t_resource_app_ext` FOR EACH ROW
BEGIN
  INSERT INTO `t_resource_detail` (`resource_id`, `resource_type`, `is_public`, `service_detail_md`, `detail_json`, `source_table`, `update_time`)
  SELECT NEW.`resource_id`, COALESCE(r.`resource_type`, 'app'), NEW.`is_public`, NEW.`service_detail_md`,
    JSON_OBJECT('app_url', NEW.`app_url`, 'embed_type', NEW.`embed_type`, 'icon', NEW.`icon`, 'screenshots', NEW.`screenshots`, 'agent_exposure', NEW.`agent_exposure`, 'agent_delivery_mode', NEW.`agent_delivery_mode`),
    't_resource_app_ext', NOW()
  FROM `t_resource` r WHERE r.`id` = NEW.`resource_id`
  ON DUPLICATE KEY UPDATE `resource_type` = VALUES(`resource_type`), `is_public` = VALUES(`is_public`), `service_detail_md` = VALUES(`service_detail_md`), `detail_json` = VALUES(`detail_json`), `source_table` = VALUES(`source_table`), `update_time` = NOW();
END$$

CREATE TRIGGER `trg_resource_app_ext_ad` AFTER DELETE ON `t_resource_app_ext` FOR EACH ROW
BEGIN
  DELETE FROM `t_resource_detail` WHERE `resource_id` = OLD.`resource_id` AND `source_table` = 't_resource_app_ext';
END$$

CREATE TRIGGER `trg_resource_dataset_ext_ai` AFTER INSERT ON `t_resource_dataset_ext` FOR EACH ROW
BEGIN
  INSERT INTO `t_resource_detail` (`resource_id`, `resource_type`, `is_public`, `service_detail_md`, `detail_json`, `source_table`, `update_time`)
  SELECT NEW.`resource_id`, COALESCE(r.`resource_type`, 'dataset'), NEW.`is_public`, NEW.`service_detail_md`,
    JSON_OBJECT('data_type', NEW.`data_type`, 'format', NEW.`format`, 'record_count', NEW.`record_count`, 'file_size', NEW.`file_size`, 'tags', NEW.`tags`),
    't_resource_dataset_ext', NOW()
  FROM `t_resource` r WHERE r.`id` = NEW.`resource_id`
  ON DUPLICATE KEY UPDATE `resource_type` = VALUES(`resource_type`), `is_public` = VALUES(`is_public`), `service_detail_md` = VALUES(`service_detail_md`), `detail_json` = VALUES(`detail_json`), `source_table` = VALUES(`source_table`), `update_time` = NOW();
END$$

CREATE TRIGGER `trg_resource_dataset_ext_au` AFTER UPDATE ON `t_resource_dataset_ext` FOR EACH ROW
BEGIN
  INSERT INTO `t_resource_detail` (`resource_id`, `resource_type`, `is_public`, `service_detail_md`, `detail_json`, `source_table`, `update_time`)
  SELECT NEW.`resource_id`, COALESCE(r.`resource_type`, 'dataset'), NEW.`is_public`, NEW.`service_detail_md`,
    JSON_OBJECT('data_type', NEW.`data_type`, 'format', NEW.`format`, 'record_count', NEW.`record_count`, 'file_size', NEW.`file_size`, 'tags', NEW.`tags`),
    't_resource_dataset_ext', NOW()
  FROM `t_resource` r WHERE r.`id` = NEW.`resource_id`
  ON DUPLICATE KEY UPDATE `resource_type` = VALUES(`resource_type`), `is_public` = VALUES(`is_public`), `service_detail_md` = VALUES(`service_detail_md`), `detail_json` = VALUES(`detail_json`), `source_table` = VALUES(`source_table`), `update_time` = NOW();
END$$

CREATE TRIGGER `trg_resource_dataset_ext_ad` AFTER DELETE ON `t_resource_dataset_ext` FOR EACH ROW
BEGIN
  DELETE FROM `t_resource_detail` WHERE `resource_id` = OLD.`resource_id` AND `source_table` = 't_resource_dataset_ext';
END$$

DELIMITER ;
