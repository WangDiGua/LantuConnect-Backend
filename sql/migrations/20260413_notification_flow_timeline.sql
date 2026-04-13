ALTER TABLE t_notification
  ADD COLUMN category varchar(32) NOT NULL DEFAULT 'notice' COMMENT 'notification category: workflow / notice / alert / system / security' AFTER type,
  ADD COLUMN severity varchar(16) NOT NULL DEFAULT 'info' COMMENT 'info / success / warning / error' AFTER category,
  ADD COLUMN aggregate_key varchar(128) NULL DEFAULT NULL COMMENT 'same user + key collapses a business flow into one inbox card' AFTER source_id,
  ADD COLUMN flow_status varchar(32) NULL DEFAULT NULL COMMENT 'running / success / failed / warning' AFTER aggregate_key,
  ADD COLUMN current_step int NULL DEFAULT NULL COMMENT 'current business flow step, 1-based' AFTER flow_status,
  ADD COLUMN total_steps int NULL DEFAULT NULL COMMENT 'total flow steps' AFTER current_step,
  ADD COLUMN steps_json json NULL COMMENT 'timeline steps shown by the frontend message center' AFTER total_steps,
  ADD COLUMN action_label varchar(64) NULL DEFAULT NULL COMMENT 'primary action label' AFTER steps_json,
  ADD COLUMN action_url varchar(256) NULL DEFAULT NULL COMMENT 'optional frontend route or external action' AFTER action_label,
  ADD COLUMN metadata_json json NULL COMMENT 'extra structured notification metadata' AFTER action_url,
  ADD COLUMN update_time datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'last row update time' AFTER create_time,
  ADD COLUMN last_event_time datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'last business event time for sorting' AFTER update_time,
  ADD INDEX idx_notification_aggregate (user_id, aggregate_key),
  ADD INDEX idx_notification_category (user_id, category, is_read),
  ADD INDEX idx_notification_last_event (user_id, last_event_time);

UPDATE t_notification
SET category = CASE
        WHEN type = 'alert' OR type LIKE '%password%' OR type LIKE '%security%' OR type LIKE '%revoked%' THEN 'alert'
        WHEN type LIKE 'system_%' OR type = 'system' THEN 'system'
        ELSE 'notice'
    END,
    severity = CASE
        WHEN type = 'alert' OR type LIKE '%rejected%' OR type LIKE '%revoked%' OR type LIKE '%deprecated%' THEN 'warning'
        WHEN type LIKE '%approved%' OR type LIKE '%published%' OR type LIKE '%created%' THEN 'success'
        ELSE 'info'
    END,
    last_event_time = COALESCE(create_time, NOW()),
    update_time = COALESCE(create_time, NOW())
WHERE category = 'notice'
  AND severity = 'info';
