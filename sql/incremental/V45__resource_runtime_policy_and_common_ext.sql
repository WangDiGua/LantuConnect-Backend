-- 资源治理表规范化：
-- 1) 合并 t_resource_health_config + t_resource_circuit_breaker -> t_resource_runtime_policy
-- 2) 提取 Agent/Skill/MCP 公共扩展字段 -> t_resource_common_ext（先回填，不破坏旧字段）

CREATE TABLE IF NOT EXISTS `t_resource_runtime_policy` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `resource_id` bigint NOT NULL,
  `resource_type` varchar(16) NOT NULL,
  `resource_code` varchar(128) NOT NULL,
  `display_name` varchar(128) NOT NULL,
  `check_type` varchar(16) DEFAULT 'http',
  `check_url` varchar(512) DEFAULT NULL,
  `interval_sec` int DEFAULT 30,
  `healthy_threshold` int DEFAULT 3,
  `timeout_sec` int DEFAULT 10,
  `health_status` varchar(16) DEFAULT 'healthy',
  `last_check_time` datetime DEFAULT NULL,
  `current_state` varchar(16) DEFAULT 'CLOSED',
  `failure_threshold` int DEFAULT 5,
  `open_duration_sec` int DEFAULT 60,
  `half_open_max_calls` int DEFAULT 3,
  `fallback_resource_code` varchar(128) DEFAULT NULL,
  `fallback_message` text,
  `last_opened_at` datetime DEFAULT NULL,
  `success_count` bigint DEFAULT 0,
  `failure_count` bigint DEFAULT 0,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_runtime_policy_resource` (`resource_id`),
  KEY `idx_runtime_policy_type_health` (`resource_type`, `health_status`),
  KEY `idx_runtime_policy_state` (`current_state`),
  KEY `idx_runtime_policy_type_code` (`resource_type`, `resource_code`),
  CONSTRAINT `fk_runtime_policy_resource` FOREIGN KEY (`resource_id`) REFERENCES `t_resource` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='资源运行治理策略（健康+熔断）';

INSERT INTO t_resource_runtime_policy (
    resource_id, resource_type, resource_code, display_name,
    check_type, check_url, interval_sec, healthy_threshold, timeout_sec, health_status, last_check_time,
    current_state, failure_threshold, open_duration_sec, half_open_max_calls,
    fallback_resource_code, fallback_message, last_opened_at, success_count, failure_count,
    create_time, update_time
)
SELECT
    h.resource_id,
    LOWER(TRIM(COALESCE(h.resource_type, cb.resource_type))),
    COALESCE(NULLIF(TRIM(h.resource_code), ''), NULLIF(TRIM(cb.resource_code), '')),
    COALESCE(NULLIF(TRIM(h.display_name), ''), NULLIF(TRIM(cb.display_name), ''), COALESCE(NULLIF(TRIM(h.resource_code), ''), NULLIF(TRIM(cb.resource_code), ''))),
    COALESCE(NULLIF(TRIM(h.check_type), ''), 'http'),
    h.check_url,
    COALESCE(h.interval_sec, 30),
    COALESCE(h.healthy_threshold, 3),
    COALESCE(h.timeout_sec, 10),
    COALESCE(NULLIF(TRIM(h.health_status), ''), 'healthy'),
    h.last_check_time,
    COALESCE(NULLIF(TRIM(cb.current_state), ''), 'CLOSED'),
    COALESCE(cb.failure_threshold, 5),
    COALESCE(cb.open_duration_sec, 60),
    COALESCE(cb.half_open_max_calls, 3),
    cb.fallback_resource_code,
    cb.fallback_message,
    cb.last_opened_at,
    COALESCE(cb.success_count, 0),
    COALESCE(cb.failure_count, 0),
    COALESCE(h.create_time, cb.create_time, NOW()),
    COALESCE(h.update_time, cb.update_time, NOW())
FROM t_resource_health_config h
LEFT JOIN t_resource_circuit_breaker cb ON cb.resource_id = h.resource_id
ON DUPLICATE KEY UPDATE
    resource_type = VALUES(resource_type),
    resource_code = VALUES(resource_code),
    display_name = VALUES(display_name),
    check_type = VALUES(check_type),
    check_url = VALUES(check_url),
    interval_sec = VALUES(interval_sec),
    healthy_threshold = VALUES(healthy_threshold),
    timeout_sec = VALUES(timeout_sec),
    health_status = VALUES(health_status),
    last_check_time = VALUES(last_check_time),
    current_state = COALESCE(VALUES(current_state), t_resource_runtime_policy.current_state),
    failure_threshold = COALESCE(VALUES(failure_threshold), t_resource_runtime_policy.failure_threshold),
    open_duration_sec = COALESCE(VALUES(open_duration_sec), t_resource_runtime_policy.open_duration_sec),
    half_open_max_calls = COALESCE(VALUES(half_open_max_calls), t_resource_runtime_policy.half_open_max_calls),
    fallback_resource_code = COALESCE(VALUES(fallback_resource_code), t_resource_runtime_policy.fallback_resource_code),
    fallback_message = COALESCE(VALUES(fallback_message), t_resource_runtime_policy.fallback_message),
    last_opened_at = COALESCE(VALUES(last_opened_at), t_resource_runtime_policy.last_opened_at),
    success_count = COALESCE(VALUES(success_count), t_resource_runtime_policy.success_count),
    failure_count = COALESCE(VALUES(failure_count), t_resource_runtime_policy.failure_count),
    update_time = NOW();

INSERT INTO t_resource_runtime_policy (
    resource_id, resource_type, resource_code, display_name,
    current_state, failure_threshold, open_duration_sec, half_open_max_calls,
    fallback_resource_code, fallback_message, last_opened_at, success_count, failure_count,
    create_time, update_time
)
SELECT
    cb.resource_id,
    LOWER(TRIM(cb.resource_type)),
    cb.resource_code,
    COALESCE(NULLIF(TRIM(cb.display_name), ''), cb.resource_code),
    COALESCE(NULLIF(TRIM(cb.current_state), ''), 'CLOSED'),
    COALESCE(cb.failure_threshold, 5),
    COALESCE(cb.open_duration_sec, 60),
    COALESCE(cb.half_open_max_calls, 3),
    cb.fallback_resource_code,
    cb.fallback_message,
    cb.last_opened_at,
    COALESCE(cb.success_count, 0),
    COALESCE(cb.failure_count, 0),
    COALESCE(cb.create_time, NOW()),
    COALESCE(cb.update_time, NOW())
FROM t_resource_circuit_breaker cb
LEFT JOIN t_resource_runtime_policy rp ON rp.resource_id = cb.resource_id
WHERE rp.resource_id IS NULL
ON DUPLICATE KEY UPDATE
    resource_type = VALUES(resource_type),
    resource_code = VALUES(resource_code),
    display_name = VALUES(display_name),
    current_state = VALUES(current_state),
    failure_threshold = VALUES(failure_threshold),
    open_duration_sec = VALUES(open_duration_sec),
    half_open_max_calls = VALUES(half_open_max_calls),
    fallback_resource_code = COALESCE(VALUES(fallback_resource_code), t_resource_runtime_policy.fallback_resource_code),
    fallback_message = COALESCE(VALUES(fallback_message), t_resource_runtime_policy.fallback_message),
    last_opened_at = COALESCE(VALUES(last_opened_at), t_resource_runtime_policy.last_opened_at),
    success_count = COALESCE(VALUES(success_count), t_resource_runtime_policy.success_count),
    failure_count = COALESCE(VALUES(failure_count), t_resource_runtime_policy.failure_count),
    update_time = NOW();

CREATE TABLE IF NOT EXISTS `t_resource_common_ext` (
  `resource_id` bigint NOT NULL,
  `is_public` tinyint(1) DEFAULT 0,
  `service_detail_md` mediumtext,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`resource_id`),
  CONSTRAINT `fk_resource_common_ext_resource` FOREIGN KEY (`resource_id`) REFERENCES `t_resource` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='资源公共扩展字段';

INSERT INTO t_resource_common_ext (resource_id, is_public, service_detail_md)
SELECT
    r.resource_id,
    GREATEST(COALESCE(a.is_public, 0), COALESCE(s.is_public, 0), 0) AS is_public,
    COALESCE(
        NULLIF(TRIM(a.service_detail_md), ''),
        NULLIF(TRIM(s.service_detail_md), ''),
        NULLIF(TRIM(m.service_detail_md), '')
    ) AS service_detail_md
FROM (
    SELECT resource_id FROM t_resource_agent_ext
    UNION
    SELECT resource_id FROM t_resource_skill_ext
    UNION
    SELECT resource_id FROM t_resource_mcp_ext
) r
LEFT JOIN t_resource_agent_ext a ON a.resource_id = r.resource_id
LEFT JOIN t_resource_skill_ext s ON s.resource_id = r.resource_id
LEFT JOIN t_resource_mcp_ext m ON m.resource_id = r.resource_id
ON DUPLICATE KEY UPDATE
    is_public = VALUES(is_public),
    service_detail_md = COALESCE(VALUES(service_detail_md), t_resource_common_ext.service_detail_md),
    update_time = NOW();
