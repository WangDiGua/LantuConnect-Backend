-- 监控中心与「五类统一资源」对齐的可选数据修补（演示/本地环境）
-- 1) 示例告警 labels 增加 resource_type，便于 /monitoring/alerts?resourceType= 筛选验收

UPDATE t_alert_record
SET labels = JSON_MERGE_PATCH(COALESCE(labels, JSON_OBJECT()), JSON_OBJECT('resource_type', 'agent'))
WHERE id = 'arec-001';

UPDATE t_alert_record
SET labels = JSON_MERGE_PATCH(COALESCE(labels, JSON_OBJECT()), JSON_OBJECT('resource_type', 'mcp'))
WHERE id = 'arec-002';
