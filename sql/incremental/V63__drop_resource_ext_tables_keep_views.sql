-- The application now writes resource details to t_resource_detail.
-- Legacy extension base tables are replaced by read-only compatibility views
-- so existing SELECT statements keep their previous result shape.

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

DROP TABLE IF EXISTS `t_resource_agent_ext`;
DROP TABLE IF EXISTS `t_resource_skill_ext`;
DROP TABLE IF EXISTS `t_resource_mcp_ext`;
DROP TABLE IF EXISTS `t_resource_app_ext`;
DROP TABLE IF EXISTS `t_resource_dataset_ext`;

CREATE OR REPLACE VIEW `t_resource_agent_ext` AS
SELECT
  `resource_id`,
  JSON_UNQUOTE(JSON_EXTRACT(`detail_json`, '$.agent_type')) AS `agent_type`,
  JSON_UNQUOTE(JSON_EXTRACT(`detail_json`, '$.mode')) AS `mode`,
  JSON_EXTRACT(`detail_json`, '$.spec_json') AS `spec_json`,
  `is_public`,
  CAST(JSON_UNQUOTE(JSON_EXTRACT(`detail_json`, '$.hidden')) AS UNSIGNED) AS `hidden`,
  CAST(JSON_UNQUOTE(JSON_EXTRACT(`detail_json`, '$.max_concurrency')) AS SIGNED) AS `max_concurrency`,
  CAST(JSON_UNQUOTE(JSON_EXTRACT(`detail_json`, '$.max_steps')) AS SIGNED) AS `max_steps`,
  CAST(JSON_UNQUOTE(JSON_EXTRACT(`detail_json`, '$.temperature')) AS DECIMAL(3,2)) AS `temperature`,
  JSON_UNQUOTE(JSON_EXTRACT(`detail_json`, '$.system_prompt')) AS `system_prompt`,
  `service_detail_md`,
  CAST(JSON_UNQUOTE(JSON_EXTRACT(`detail_json`, '$.featured')) AS UNSIGNED) AS `featured`,
  CAST(JSON_UNQUOTE(JSON_EXTRACT(`detail_json`, '$.rating_avg')) AS DECIMAL(3,2)) AS `rating_avg`,
  CAST(JSON_UNQUOTE(JSON_EXTRACT(`detail_json`, '$.rating_count')) AS SIGNED) AS `rating_count`,
  JSON_UNQUOTE(JSON_EXTRACT(`detail_json`, '$.registration_protocol')) AS `registration_protocol`,
  JSON_UNQUOTE(JSON_EXTRACT(`detail_json`, '$.upstream_endpoint')) AS `upstream_endpoint`,
  JSON_UNQUOTE(JSON_EXTRACT(`detail_json`, '$.upstream_agent_id')) AS `upstream_agent_id`,
  JSON_UNQUOTE(JSON_EXTRACT(`detail_json`, '$.credential_ref')) AS `credential_ref`,
  JSON_UNQUOTE(JSON_EXTRACT(`detail_json`, '$.transform_profile')) AS `transform_profile`,
  JSON_UNQUOTE(JSON_EXTRACT(`detail_json`, '$.model_alias')) AS `model_alias`,
  CAST(JSON_UNQUOTE(JSON_EXTRACT(`detail_json`, '$.enabled')) AS UNSIGNED) AS `enabled`
FROM `t_resource_detail`
WHERE `source_table` = 't_resource_agent_ext';

CREATE OR REPLACE VIEW `t_resource_skill_ext` AS
SELECT
  `resource_id`,
  JSON_UNQUOTE(JSON_EXTRACT(`detail_json`, '$.skill_type')) AS `skill_type`,
  JSON_UNQUOTE(JSON_EXTRACT(`detail_json`, '$.execution_mode')) AS `execution_mode`,
  JSON_EXTRACT(`detail_json`, '$.manifest_json') AS `manifest_json`,
  JSON_UNQUOTE(JSON_EXTRACT(`detail_json`, '$.entry_doc')) AS `entry_doc`,
  JSON_UNQUOTE(JSON_EXTRACT(`detail_json`, '$.mode')) AS `mode`,
  CAST(JSON_UNQUOTE(JSON_EXTRACT(`detail_json`, '$.parent_resource_id')) AS SIGNED) AS `parent_resource_id`,
  JSON_UNQUOTE(JSON_EXTRACT(`detail_json`, '$.display_template')) AS `display_template`,
  JSON_EXTRACT(`detail_json`, '$.spec_json') AS `spec_json`,
  JSON_EXTRACT(`detail_json`, '$.parameters_schema') AS `parameters_schema`,
  `is_public`,
  CAST(JSON_UNQUOTE(JSON_EXTRACT(`detail_json`, '$.max_concurrency')) AS SIGNED) AS `max_concurrency`,
  JSON_UNQUOTE(JSON_EXTRACT(`detail_json`, '$.pack_validation_status')) AS `pack_validation_status`,
  CAST(JSON_UNQUOTE(JSON_EXTRACT(`detail_json`, '$.pack_validated_at')) AS DATETIME) AS `pack_validated_at`,
  JSON_UNQUOTE(JSON_EXTRACT(`detail_json`, '$.pack_validation_message')) AS `pack_validation_message`,
  JSON_UNQUOTE(JSON_EXTRACT(`detail_json`, '$.skill_root_path')) AS `skill_root_path`,
  `service_detail_md`,
  JSON_UNQUOTE(JSON_EXTRACT(`detail_json`, '$.hosted_system_prompt')) AS `hosted_system_prompt`,
  JSON_UNQUOTE(JSON_EXTRACT(`detail_json`, '$.hosted_user_template')) AS `hosted_user_template`,
  JSON_UNQUOTE(JSON_EXTRACT(`detail_json`, '$.hosted_default_model')) AS `hosted_default_model`,
  JSON_EXTRACT(`detail_json`, '$.hosted_output_schema') AS `hosted_output_schema`,
  CAST(JSON_UNQUOTE(JSON_EXTRACT(`detail_json`, '$.hosted_temperature')) AS DECIMAL(3,2)) AS `hosted_temperature`
FROM `t_resource_detail`
WHERE `source_table` = 't_resource_skill_ext';

CREATE OR REPLACE VIEW `t_resource_mcp_ext` AS
SELECT
  `resource_id`,
  JSON_UNQUOTE(JSON_EXTRACT(`detail_json`, '$.endpoint')) AS `endpoint`,
  JSON_UNQUOTE(JSON_EXTRACT(`detail_json`, '$.protocol')) AS `protocol`,
  JSON_UNQUOTE(JSON_EXTRACT(`detail_json`, '$.auth_type')) AS `auth_type`,
  JSON_EXTRACT(`detail_json`, '$.auth_config') AS `auth_config`,
  `service_detail_md`
FROM `t_resource_detail`
WHERE `source_table` = 't_resource_mcp_ext';

CREATE OR REPLACE VIEW `t_resource_app_ext` AS
SELECT
  `resource_id`,
  JSON_UNQUOTE(JSON_EXTRACT(`detail_json`, '$.app_url')) AS `app_url`,
  JSON_UNQUOTE(JSON_EXTRACT(`detail_json`, '$.embed_type')) AS `embed_type`,
  JSON_UNQUOTE(JSON_EXTRACT(`detail_json`, '$.icon')) AS `icon`,
  JSON_EXTRACT(`detail_json`, '$.screenshots') AS `screenshots`,
  `is_public`,
  `service_detail_md`,
  JSON_UNQUOTE(JSON_EXTRACT(`detail_json`, '$.agent_exposure')) AS `agent_exposure`,
  JSON_UNQUOTE(JSON_EXTRACT(`detail_json`, '$.agent_delivery_mode')) AS `agent_delivery_mode`
FROM `t_resource_detail`
WHERE `source_table` = 't_resource_app_ext';

CREATE OR REPLACE VIEW `t_resource_dataset_ext` AS
SELECT
  `resource_id`,
  JSON_UNQUOTE(JSON_EXTRACT(`detail_json`, '$.data_type')) AS `data_type`,
  JSON_UNQUOTE(JSON_EXTRACT(`detail_json`, '$.format')) AS `format`,
  CAST(JSON_UNQUOTE(JSON_EXTRACT(`detail_json`, '$.record_count')) AS SIGNED) AS `record_count`,
  CAST(JSON_UNQUOTE(JSON_EXTRACT(`detail_json`, '$.file_size')) AS SIGNED) AS `file_size`,
  JSON_EXTRACT(`detail_json`, '$.tags') AS `tags`,
  `is_public`,
  `service_detail_md`
FROM `t_resource_detail`
WHERE `source_table` = 't_resource_dataset_ext';
