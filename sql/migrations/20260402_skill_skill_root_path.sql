-- skill_root_path (idempotent; avoids IF NOT EXISTS + driver encoding issues on some clients)
SET NAMES utf8mb4;

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 't_resource_skill_ext'
      AND COLUMN_NAME = 'skill_root_path'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE t_resource_skill_ext ADD COLUMN skill_root_path VARCHAR(512) NULL DEFAULT NULL COMMENT ''zip skill root subtree'' AFTER pack_validation_message',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
