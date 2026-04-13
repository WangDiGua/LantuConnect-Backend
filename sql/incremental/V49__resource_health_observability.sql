-- 统一资源健康观测字段：agent / skill / mcp 共用同一张运行态表，避免再拆分健康表
ALTER TABLE `t_resource_runtime_policy`
    ADD COLUMN `probe_strategy` varchar(32) DEFAULT NULL COMMENT '探测策略：agent_provider / skill_dependency / mcp_jsonrpc / mcp_stdio / http' AFTER `check_url`,
    ADD COLUMN `last_probe_at` datetime DEFAULT NULL COMMENT '最近一次探测时间' AFTER `callability_reason`,
    ADD COLUMN `last_success_at` datetime DEFAULT NULL COMMENT '最近一次健康探测成功时间' AFTER `last_probe_at`,
    ADD COLUMN `last_failure_at` datetime DEFAULT NULL COMMENT '最近一次健康探测失败时间' AFTER `last_success_at`,
    ADD COLUMN `last_failure_reason` varchar(1024) DEFAULT NULL COMMENT '最近一次失败原因' AFTER `last_failure_at`,
    ADD COLUMN `consecutive_success` bigint DEFAULT 0 COMMENT '连续成功次数' AFTER `last_failure_reason`,
    ADD COLUMN `consecutive_failure` bigint DEFAULT 0 COMMENT '连续失败次数' AFTER `consecutive_success`,
    ADD COLUMN `probe_latency_ms` bigint DEFAULT NULL COMMENT '最近一次探测耗时（毫秒）' AFTER `consecutive_failure`,
    ADD COLUMN `probe_payload_summary` varchar(1024) DEFAULT NULL COMMENT '最近一次探测载荷摘要' AFTER `probe_latency_ms`,
    ADD COLUMN `callability_state` varchar(32) DEFAULT 'not_configured' COMMENT '可调用状态：callable / not_published / disabled / health_down / health_degraded / circuit_open / circuit_half_open / dependency_blocked' AFTER `current_state`,
    ADD COLUMN `callability_reason` varchar(1024) DEFAULT NULL COMMENT '可调用状态原因' AFTER `callability_state`;

UPDATE t_resource_runtime_policy
SET probe_strategy = CASE
        WHEN resource_type = 'agent' THEN 'agent_provider'
        WHEN resource_type = 'skill' THEN 'skill_dependency'
        WHEN resource_type = 'mcp' AND LOWER(COALESCE(check_type, '')) = 'stdio' THEN 'mcp_stdio'
        WHEN resource_type = 'mcp' THEN 'mcp_jsonrpc'
        ELSE COALESCE(NULLIF(TRIM(probe_strategy), ''), 'http')
    END,
    callability_state = CASE
        WHEN LOWER(COALESCE(health_status, '')) IN ('down', 'disabled') THEN LOWER(COALESCE(health_status, ''))
        WHEN UPPER(COALESCE(current_state, 'CLOSED')) = 'OPEN' THEN 'circuit_open'
        WHEN UPPER(COALESCE(current_state, 'CLOSED')) = 'HALF_OPEN' THEN 'circuit_half_open'
        WHEN LOWER(COALESCE(health_status, '')) = 'degraded' THEN 'health_degraded'
        WHEN LOWER(COALESCE(health_status, '')) = 'healthy' THEN 'callable'
        ELSE 'not_configured'
    END,
    callability_reason = CASE
        WHEN LOWER(COALESCE(health_status, '')) = 'healthy' AND UPPER(COALESCE(current_state, 'CLOSED')) = 'CLOSED' THEN '资源健康且熔断闭合，可分发'
        WHEN LOWER(COALESCE(health_status, '')) = 'degraded' THEN '资源健康状态为 degraded'
        WHEN LOWER(COALESCE(health_status, '')) = 'down' THEN '资源健康状态为 down'
        WHEN LOWER(COALESCE(health_status, '')) = 'disabled' THEN '资源已禁用'
        WHEN UPPER(COALESCE(current_state, 'CLOSED')) = 'OPEN' THEN '熔断已打开'
        WHEN UPPER(COALESCE(current_state, 'CLOSED')) = 'HALF_OPEN' THEN '熔断处于半开态'
        ELSE callability_reason
    END,
    update_time = NOW();

CREATE INDEX `idx_runtime_policy_callability` ON `t_resource_runtime_policy` (`resource_type`, `callability_state`);
CREATE INDEX `idx_runtime_policy_probe` ON `t_resource_runtime_policy` (`resource_type`, `probe_strategy`);
