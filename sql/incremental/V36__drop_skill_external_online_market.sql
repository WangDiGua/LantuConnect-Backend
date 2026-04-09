-- Remove online / external skills market: engagement, mirror snapshot, sync state, and admin override row.

DROP TABLE IF EXISTS `t_skill_external_favorite`;
DROP TABLE IF EXISTS `t_skill_external_review`;
DROP TABLE IF EXISTS `t_skill_external_download_event`;
DROP TABLE IF EXISTS `t_skill_external_view_event`;
DROP TABLE IF EXISTS `t_skill_external_catalog_item`;
DROP TABLE IF EXISTS `t_skill_external_catalog_sync`;

DELETE FROM `t_system_param` WHERE `key` = 'skill_external_catalog';
