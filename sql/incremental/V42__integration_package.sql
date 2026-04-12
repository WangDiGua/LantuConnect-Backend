-- 集成套餐（对外门户可见包）：一条配置对应一批资源，绑定到 API Key 后仅暴露包内资源

START TRANSACTION;

CREATE TABLE IF NOT EXISTS t_integration_package (
  id            VARCHAR(36)  NOT NULL PRIMARY KEY COMMENT 'UUID',
  name          VARCHAR(128) NOT NULL,
  description   VARCHAR(512) NULL,
  status        VARCHAR(16)  NOT NULL DEFAULT 'active' COMMENT 'active | disabled',
  created_by    VARCHAR(64)  NULL,
  create_time   DATETIME     NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_integration_pkg_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='集成套餐（资源白名单包）';

CREATE TABLE IF NOT EXISTS t_integration_package_item (
  package_id    VARCHAR(36)  NOT NULL,
  resource_type VARCHAR(16)  NOT NULL,
  resource_id   BIGINT       NOT NULL,
  PRIMARY KEY (package_id, resource_type, resource_id),
  KEY idx_pkg_item_lookup (resource_type, resource_id),
  CONSTRAINT fk_pkg_item_package FOREIGN KEY (package_id) REFERENCES t_integration_package (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='集成套餐资源项';

SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_api_key' AND COLUMN_NAME = 'integration_package_id');
SET @sql := IF(@exist = 0,
  'ALTER TABLE t_api_key ADD COLUMN integration_package_id VARCHAR(36) NULL COMMENT ''绑定集成套餐时仅允许访问包内资源（与 scopes 并存时优先按包裁剪）'' AFTER scopes',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

COMMIT;
