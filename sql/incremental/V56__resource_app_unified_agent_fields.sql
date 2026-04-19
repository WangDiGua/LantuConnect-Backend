ALTER TABLE `t_resource_app_ext`
  ADD COLUMN `agent_exposure` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '页面型 Agent 暴露形态；unified_agent 表示在 Agent 视图中展示' AFTER `service_detail_md`,
  ADD COLUMN `agent_delivery_mode` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '页面型 Agent 交付模式；当前仅 page' AFTER `agent_exposure`;
