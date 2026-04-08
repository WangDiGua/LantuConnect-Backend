-- Hosted Skill 列；绑定类型 agent_depends_mcp / mcp_depends_skill 沿用 t_resource_relation（无新表）

ALTER TABLE t_resource_skill_ext
    ADD COLUMN execution_mode VARCHAR(16) NOT NULL DEFAULT 'pack' COMMENT 'pack|hosted' AFTER skill_type,
    ADD COLUMN hosted_system_prompt TEXT NULL COMMENT 'hosted 系统提示' AFTER service_detail_md,
    ADD COLUMN hosted_user_template MEDIUMTEXT NULL COMMENT '用户消息模板，可含占位符 {{input}}' AFTER hosted_system_prompt,
    ADD COLUMN hosted_default_model VARCHAR(128) NULL COMMENT '默认 chat 模型' AFTER hosted_user_template,
    ADD COLUMN hosted_output_schema JSON NULL COMMENT '可选输出 JSON Schema' AFTER hosted_default_model,
    ADD COLUMN hosted_temperature DECIMAL(3, 2) NULL COMMENT '可选温度' AFTER hosted_output_schema;
