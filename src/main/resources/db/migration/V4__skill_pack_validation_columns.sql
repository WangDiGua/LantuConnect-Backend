-- 与 sql/migrations/20260401_skill_pack_validation.sql 对齐；未开启 Flyway 时需手动执行该 sql 文件。
-- MySQL 8+：ADD COLUMN IF NOT EXISTS

SET NAMES utf8mb4;

ALTER TABLE t_resource_skill_ext
    ADD COLUMN IF NOT EXISTS pack_validation_status VARCHAR(16) NOT NULL DEFAULT 'none' COMMENT 'none pending valid invalid' AFTER max_concurrency,
    ADD COLUMN IF NOT EXISTS pack_validated_at DATETIME NULL DEFAULT NULL AFTER pack_validation_status,
    ADD COLUMN IF NOT EXISTS pack_validation_message TEXT NULL AFTER pack_validated_at;

SET @idx_exists := (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 't_resource_skill_ext'
      AND index_name = 'idx_skill_pack_validation'
);
SET @idx_sql := IF(@idx_exists = 0,
                   'CREATE INDEX idx_skill_pack_validation ON t_resource_skill_ext (pack_validation_status)',
                   'SELECT 1');
PREPARE stmt FROM @idx_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
