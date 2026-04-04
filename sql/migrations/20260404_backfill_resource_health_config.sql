-- 回填资源健康检查配置（当前库中常见为空，导致「健康状态」页与依赖 t_resource_health_config 的统计无数据）。
-- 默认探测本机 Actuator：`application-local.yml` 中 server.port=8080、context-path=/regis 时请保持 URL 一致；若端口或上下文不同，请改 check_url 后执行。
-- 仅为尚未存在配置的 resource_id 插入，最多 8 条，避免一次拉满。

INSERT INTO t_resource_health_config (
  resource_id, resource_type, resource_code, display_name,
  check_type, check_url, interval_sec, healthy_threshold, timeout_sec, health_status
)
SELECT
  r.id,
  r.resource_type,
  r.resource_code,
  COALESCE(NULLIF(TRIM(r.display_name), ''), r.resource_code),
  'http',
  'http://127.0.0.1:8080/regis/actuator/health',
  60, 3, 10, 'healthy'
FROM t_resource r
WHERE r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM t_resource_health_config h WHERE h.resource_id = r.id)
ORDER BY r.id ASC
LIMIT 8;
