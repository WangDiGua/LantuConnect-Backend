-- 集成套餐改为用户自建：owner_user_id 标识归属；NULL 为历史/无效数据

START TRANSACTION;

SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_integration_package' AND COLUMN_NAME = 'owner_user_id');
SET @sql := IF(@exist = 0,
  'ALTER TABLE t_integration_package ADD COLUMN owner_user_id BIGINT NULL COMMENT ''用户自建套餐时必填；NULL 表示迁移前数据'' AFTER created_by, ADD KEY idx_integration_pkg_owner (owner_user_id)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

COMMIT;
