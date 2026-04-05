-- MCP 扩展：服务详情（Markdown，选填），用于市场详情「服务详情」Tab
ALTER TABLE `t_resource_mcp_ext`
  ADD COLUMN `service_detail_md` mediumtext NULL DEFAULT NULL COMMENT '服务详情 Markdown（选填）' AFTER `auth_config`;
