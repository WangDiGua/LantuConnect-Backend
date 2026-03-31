-- 调用日志增加资源类型，用于质量历史等按 resourceType + resourceId 聚合（与网关 invoke 写入一致）
-- 回滚: ALTER TABLE t_call_log DROP INDEX idx_call_log_resource_agent_time; ALTER TABLE t_call_log DROP COLUMN resource_type;

ALTER TABLE t_call_log
    ADD COLUMN resource_type VARCHAR(32) NULL DEFAULT NULL COMMENT '网关调用目标资源类型 agent/skill/mcp/app/dataset 等' AFTER agent_name;

CREATE INDEX idx_call_log_resource_agent_time ON t_call_log (resource_type, agent_id, create_time);
