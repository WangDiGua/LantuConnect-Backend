-- Runtime code reads and writes t_resource_detail directly now.
-- Remove temporary compatibility views created during the transition.

DROP VIEW IF EXISTS `t_resource_agent_ext`;
DROP VIEW IF EXISTS `t_resource_skill_ext`;
DROP VIEW IF EXISTS `t_resource_mcp_ext`;
DROP VIEW IF EXISTS `t_resource_app_ext`;
DROP VIEW IF EXISTS `t_resource_dataset_ext`;

UPDATE `t_resource_detail`
SET `source_table` = 'resource_detail'
WHERE `source_table` <> 'resource_detail';
