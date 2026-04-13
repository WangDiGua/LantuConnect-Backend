ALTER TABLE `t_resource_agent_ext`
    ADD COLUMN IF NOT EXISTS `registration_protocol` varchar(64) NULL DEFAULT 'openai_compatible' COMMENT 'Agent 注册协议',
    ADD COLUMN IF NOT EXISTS `upstream_endpoint` varchar(512) NULL COMMENT '上游协议入口',
    ADD COLUMN IF NOT EXISTS `upstream_agent_id` varchar(128) NULL COMMENT '上游 Agent/App ID',
    ADD COLUMN IF NOT EXISTS `credential_ref` varchar(1024) NULL COMMENT '加密后的凭据引用',
    ADD COLUMN IF NOT EXISTS `transform_profile` varchar(128) NULL COMMENT '转换档案',
    ADD COLUMN IF NOT EXISTS `model_alias` varchar(128) NULL COMMENT '对外模型别名',
    ADD COLUMN IF NOT EXISTS `enabled` tinyint(1) NULL DEFAULT 1 COMMENT '是否可调用';

ALTER TABLE `t_resource_agent_ext`
    ADD INDEX `idx_agent_ext_model_alias`(`model_alias`);

