-- =============================================================================
-- Migration: 20260324_01_safe_add_resource_version.sql
-- Purpose  : Safely introduce t_resource_version table for versioned resolve/invoke.
-- Author   : Cursor Agent
--
-- Execution characteristics:
-- 1) Idempotent: can be executed multiple times safely.
-- 2) Non-destructive: does NOT drop/alter existing business tables.
-- 3) Backfill-safe: only inserts missing (resource_id, version='v1') rows.
--
-- Recommended execution:
-- - Run in off-peak window.
-- - Backup schema and core tables before execution.
-- - Verify results using the checks at the end of this script.
-- =============================================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- Step 1) Create table if missing.
-- Notes:
-- - status defaults to 'active'
-- - is_current marks the preferred version for a resource
-- - fk_resource_version_resource keeps referential consistency with t_resource
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_resource_version` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `resource_id` BIGINT NOT NULL,
  `version` VARCHAR(32) NOT NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'active',
  `is_current` TINYINT(1) NULL DEFAULT 0,
  `snapshot_json` JSON NULL,
  `create_time` DATETIME NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_resource_version` (`resource_id`, `version`),
  KEY `idx_resource_current` (`resource_id`, `is_current`, `create_time`),
  CONSTRAINT `fk_resource_version_resource`
    FOREIGN KEY (`resource_id`) REFERENCES `t_resource` (`id`)
    ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='资源版本表';

-- -----------------------------------------------------------------------------
-- Step 2) Backfill default v1 for existing resources.
-- Notes:
-- - Only inserts resources that do not already have version='v1'
-- - Existing rows are left untouched
-- -----------------------------------------------------------------------------
INSERT INTO `t_resource_version` (`resource_id`, `version`, `status`, `is_current`, `snapshot_json`, `create_time`)
SELECT r.`id`, 'v1', 'active', 1, NULL, NOW()
FROM `t_resource` r
LEFT JOIN `t_resource_version` rv
  ON rv.`resource_id` = r.`id`
 AND rv.`version` = 'v1'
WHERE r.`deleted` = 0
  AND rv.`id` IS NULL;

-- -----------------------------------------------------------------------------
-- Step 3) Ensure every resource has exactly one current version.
-- Notes:
-- - If a resource has no current version, the latest row by create_time/id is set.
-- - This update is safe and repeatable.
-- -----------------------------------------------------------------------------
UPDATE `t_resource_version` rv
JOIN (
  SELECT t.`resource_id`, MAX(t.`id`) AS latest_id
  FROM `t_resource_version` t
  WHERE t.`resource_id` IN (
    SELECT x.`resource_id`
    FROM (
      SELECT `resource_id`
      FROM `t_resource_version`
      GROUP BY `resource_id`
      HAVING SUM(CASE WHEN `is_current` = 1 THEN 1 ELSE 0 END) = 0
    ) x
  )
  GROUP BY t.`resource_id`
) m ON m.latest_id = rv.`id`
SET rv.`is_current` = 1;

-- -----------------------------------------------------------------------------
-- Post-check 1: table row count should be >= active resources count.
-- -----------------------------------------------------------------------------
SELECT
  (SELECT COUNT(*) FROM `t_resource` WHERE `deleted` = 0) AS active_resource_count,
  (SELECT COUNT(*) FROM `t_resource_version`) AS resource_version_count;

-- -----------------------------------------------------------------------------
-- Post-check 2: should return 0 rows (no resources missing current version).
-- -----------------------------------------------------------------------------
SELECT `resource_id`
FROM `t_resource_version`
GROUP BY `resource_id`
HAVING SUM(CASE WHEN `is_current` = 1 THEN 1 ELSE 0 END) = 0;

-- -----------------------------------------------------------------------------
-- Rollback (manual, execute only if you need to revert and you have no dependency):
-- 1) DROP TABLE t_resource_version;
-- IMPORTANT: rollback is destructive and should be done only after impact assessment.
-- -----------------------------------------------------------------------------
