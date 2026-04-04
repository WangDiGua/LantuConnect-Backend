-- 与 sql/migrations/20260401_skill_pack_validation.sql 对齐；在 MySQL 中按需手工执行。
-- MySQL 8.0：无 ADD COLUMN IF NOT EXISTS，使用 information_schema + 动态 SQL 保证幂等。

SET NAMES utf8mb4;

SET @c1 := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 't_resource_skill_ext'
      AND COLUMN_NAME = 'pack_validation_status');
SET @s1 := IF(@c1 = 0,
    'ALTER TABLE t_resource_skill_ext ADD COLUMN pack_validation_status VARCHAR(16) NOT NULL DEFAULT ''none'' COMMENT ''none pending valid invalid'' AFTER max_concurrency',
    'SELECT 1');
PREPARE p1 FROM @s1;
EXECUTE p1;
DEALLOCATE PREPARE p1;

SET @c2 := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 't_resource_skill_ext'
      AND COLUMN_NAME = 'pack_validated_at');
SET @s2 := IF(@c2 = 0,
    'ALTER TABLE t_resource_skill_ext ADD COLUMN pack_validated_at DATETIME NULL DEFAULT NULL AFTER pack_validation_status',
    'SELECT 1');
PREPARE p2 FROM @s2;
EXECUTE p2;
DEALLOCATE PREPARE p2;

SET @c3 := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 't_resource_skill_ext'
      AND COLUMN_NAME = 'pack_validation_message');
SET @s3 := IF(@c3 = 0,
    'ALTER TABLE t_resource_skill_ext ADD COLUMN pack_validation_message TEXT NULL AFTER pack_validated_at',
    'SELECT 1');
PREPARE p3 FROM @s3;
EXECUTE p3;
DEALLOCATE PREPARE p3;

SET @idx_exists := (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 't_resource_skill_ext'
      AND index_name = 'idx_skill_pack_validation');
SET @idx_sql := IF(@idx_exists = 0,
                   'CREATE INDEX idx_skill_pack_validation ON t_resource_skill_ext (pack_validation_status)',
                   'SELECT 1');
PREPARE stmt FROM @idx_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
