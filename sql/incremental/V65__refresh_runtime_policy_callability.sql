-- Recompute stored callability after resource detail consolidation and circuit state changes.
-- The application does not auto-run these files; apply manually with the incremental chain.

UPDATE `t_resource_runtime_policy` p
JOIN `t_resource` r ON r.`id` = p.`resource_id` AND r.`deleted` = 0
LEFT JOIN `t_resource_detail` d ON d.`resource_id` = p.`resource_id`
SET
  p.`callability_state` = CASE
    WHEN LOWER(COALESCE(r.`status`, '')) <> 'published' THEN 'not_published'
    WHEN p.`resource_type` = 'agent'
      AND LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(d.`detail_json`, '$.enabled')), 'true')) IN ('0', 'false', 'no', 'off') THEN 'disabled'
    WHEN LOWER(COALESCE(p.`health_status`, '')) = 'disabled' THEN 'disabled'
    WHEN UPPER(COALESCE(p.`current_state`, '')) IN ('OPEN', 'FORCED_OPEN') THEN 'circuit_open'
    WHEN UPPER(COALESCE(p.`current_state`, '')) = 'HALF_OPEN' THEN 'circuit_half_open'
    WHEN LOWER(COALESCE(p.`health_status`, '')) = 'down' THEN 'health_down'
    WHEN p.`callability_state` = 'dependency_blocked' THEN 'dependency_blocked'
    ELSE 'callable'
  END,
  p.`callability_reason` = CASE
    WHEN LOWER(COALESCE(r.`status`, '')) <> 'published' THEN 'resource is not published'
    WHEN p.`resource_type` = 'agent'
      AND LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(d.`detail_json`, '$.enabled')), 'true')) IN ('0', 'false', 'no', 'off') THEN 'resource is disabled'
    WHEN LOWER(COALESCE(p.`health_status`, '')) = 'disabled' THEN 'resource is disabled'
    WHEN UPPER(COALESCE(p.`current_state`, '')) IN ('OPEN', 'FORCED_OPEN') THEN 'circuit breaker is open'
    WHEN UPPER(COALESCE(p.`current_state`, '')) = 'HALF_OPEN' THEN 'circuit breaker is half open'
    WHEN LOWER(COALESCE(p.`health_status`, '')) = 'down' THEN COALESCE(NULLIF(TRIM(p.`last_failure_reason`), ''), 'health probe reported down')
    WHEN p.`callability_state` = 'dependency_blocked' THEN COALESCE(NULLIF(TRIM(p.`callability_reason`), ''), 'dependency blocked')
    WHEN LOWER(COALESCE(p.`health_status`, '')) = 'degraded' THEN 'resource callable with degraded health'
    ELSE 'resource callable'
  END,
  p.`update_time` = NOW()
WHERE p.`resource_type` IN ('agent', 'skill', 'mcp');
