-- 市场详情「介绍」Tab：Markdown，选填（与 MCP 一致）

ALTER TABLE `t_resource_agent_ext`
  ADD COLUMN `service_detail_md` mediumtext NULL DEFAULT NULL COMMENT '介绍 Markdown（选填）' AFTER `system_prompt`;

ALTER TABLE `t_resource_skill_ext`
  ADD COLUMN `service_detail_md` mediumtext NULL DEFAULT NULL COMMENT '技能介绍 Markdown（选填）' AFTER `skill_root_path`;

ALTER TABLE `t_resource_app_ext`
  ADD COLUMN `service_detail_md` mediumtext NULL DEFAULT NULL COMMENT '应用介绍 Markdown（选填）' AFTER `is_public`;

ALTER TABLE `t_resource_dataset_ext`
  ADD COLUMN `service_detail_md` mediumtext NULL DEFAULT NULL COMMENT '数据集介绍 Markdown（选填）' AFTER `is_public`;
