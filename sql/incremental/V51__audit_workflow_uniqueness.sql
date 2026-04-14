DELETE n
FROM t_notification n
INNER JOIN (
    SELECT user_id, aggregate_key, MAX(id) AS keep_id
    FROM t_notification
    WHERE aggregate_key IS NOT NULL
    GROUP BY user_id, aggregate_key
    HAVING COUNT(*) > 1
) d
    ON d.user_id = n.user_id
   AND d.aggregate_key = n.aggregate_key
   AND n.id <> d.keep_id;

SET @notification_index_exists := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 't_notification'
      AND index_name = 'uk_notification_user_aggregate'
);
SET @notification_index_sql := IF(
    @notification_index_exists = 0,
    'ALTER TABLE t_notification ADD UNIQUE INDEX uk_notification_user_aggregate (user_id, aggregate_key)',
    'SELECT 1'
);
PREPARE stmt FROM @notification_index_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE t_developer_application cur
INNER JOIN (
    SELECT user_id, MAX(id) AS keep_id
    FROM t_developer_application
    WHERE status = 'pending'
    GROUP BY user_id
    HAVING COUNT(*) > 1
) dup
    ON dup.user_id = cur.user_id
SET cur.status = 'rejected',
    cur.review_comment = CASE
        WHEN COALESCE(NULLIF(cur.review_comment, ''), '') = '' THEN 'system-closed duplicate pending application before unique constraint'
        ELSE CONCAT(cur.review_comment, '; system-closed duplicate pending application before unique constraint')
    END,
    cur.reviewed_at = COALESCE(cur.reviewed_at, NOW()),
    cur.update_time = NOW()
WHERE cur.status = 'pending'
  AND cur.id <> dup.keep_id;

SET @dev_guard_column_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 't_developer_application'
      AND column_name = 'pending_user_guard'
);
SET @dev_guard_column_sql := IF(
    @dev_guard_column_exists = 0,
    'ALTER TABLE t_developer_application ADD COLUMN pending_user_guard bigint GENERATED ALWAYS AS (CASE WHEN status = ''pending'' THEN user_id ELSE NULL END) STORED COMMENT ''only pending rows participate in unique user guard''',
    'SELECT 1'
);
PREPARE stmt FROM @dev_guard_column_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @dev_guard_index_exists := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 't_developer_application'
      AND index_name = 'uk_dev_apply_pending_user'
);
SET @dev_guard_index_sql := IF(
    @dev_guard_index_exists = 0,
    'ALTER TABLE t_developer_application ADD UNIQUE INDEX uk_dev_apply_pending_user (pending_user_guard)',
    'SELECT 1'
);
PREPARE stmt FROM @dev_guard_index_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE t_audit_item ai
INNER JOIN (
    SELECT target_type,
           target_id,
           COALESCE(NULLIF(TRIM(audit_kind), ''), 'initial') AS audit_kind_norm,
           MAX(id) AS keep_id
    FROM t_audit_item
    WHERE status = 'pending_review'
    GROUP BY target_type, target_id, COALESCE(NULLIF(TRIM(audit_kind), ''), 'initial')
    HAVING COUNT(*) > 1
) dup
    ON dup.target_type = ai.target_type
   AND dup.target_id = ai.target_id
   AND dup.audit_kind_norm = COALESCE(NULLIF(TRIM(ai.audit_kind), ''), 'initial')
SET ai.status = 'withdrawn',
    ai.reject_reason = CASE
        WHEN COALESCE(NULLIF(ai.reject_reason, ''), '') = '' THEN 'system-closed duplicate pending audit before unique constraint'
        ELSE ai.reject_reason
    END,
    ai.review_time = COALESCE(ai.review_time, NOW())
WHERE ai.status = 'pending_review'
  AND ai.id <> dup.keep_id;

SET @audit_target_guard_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 't_audit_item'
      AND column_name = 'pending_target_id_guard'
);
SET @audit_target_guard_sql := IF(
    @audit_target_guard_exists = 0,
    'ALTER TABLE t_audit_item ADD COLUMN pending_target_id_guard bigint GENERATED ALWAYS AS (CASE WHEN status = ''pending_review'' THEN target_id ELSE NULL END) STORED COMMENT ''only pending audit rows participate in unique target guard''',
    'SELECT 1'
);
PREPARE stmt FROM @audit_target_guard_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @audit_kind_guard_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 't_audit_item'
      AND column_name = 'pending_audit_kind_guard'
);
SET @audit_kind_guard_sql := IF(
    @audit_kind_guard_exists = 0,
    'ALTER TABLE t_audit_item ADD COLUMN pending_audit_kind_guard varchar(32) GENERATED ALWAYS AS (CASE WHEN status = ''pending_review'' THEN COALESCE(NULLIF(TRIM(audit_kind), ''''), ''initial'') ELSE NULL END) STORED COMMENT ''normalized audit kind for pending uniqueness''',
    'SELECT 1'
);
PREPARE stmt FROM @audit_kind_guard_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @audit_guard_index_exists := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 't_audit_item'
      AND index_name = 'uk_audit_item_pending_target_kind'
);
SET @audit_guard_index_sql := IF(
    @audit_guard_index_exists = 0,
    'ALTER TABLE t_audit_item ADD UNIQUE INDEX uk_audit_item_pending_target_kind (target_type, pending_target_id_guard, pending_audit_kind_guard)',
    'SELECT 1'
);
PREPARE stmt FROM @audit_guard_index_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
