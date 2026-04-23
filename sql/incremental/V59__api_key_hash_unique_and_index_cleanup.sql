-- API Key 认证按 key_hash 精确查询，数据库层必须保证唯一性，避免 selectOne 命中多行。
-- 同时清理资源运行策略表上重复的 probe_strategy 热路径索引，降低写入维护成本。

SET @drop_api_key_hash_idx := (
    SELECT IF(
        COUNT(*) > 0,
        'ALTER TABLE `t_api_key` DROP INDEX `idx_api_key_key_hash`',
        'SELECT 1'
    )
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 't_api_key'
      AND index_name = 'idx_api_key_key_hash'
      AND non_unique = 1
);
PREPARE stmt FROM @drop_api_key_hash_idx;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_api_key_hash_unique := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `t_api_key` ADD UNIQUE INDEX `uk_api_key_key_hash` (`key_hash`)',
        'SELECT 1'
    )
    FROM (
        SELECT
            index_name,
            MAX(non_unique) AS index_non_unique,
            GROUP_CONCAT(column_name ORDER BY seq_in_index) AS index_columns
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 't_api_key'
        GROUP BY index_name
        HAVING index_non_unique = 0
           AND index_columns = 'key_hash'
    ) unique_key_hash_indexes
);
PREPARE stmt FROM @add_api_key_hash_unique;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @drop_duplicate_runtime_probe_idx := (
    SELECT IF(
        COUNT(*) > 0,
        'ALTER TABLE `t_resource_runtime_policy` DROP INDEX `idx_runtime_policy_probe_strategy`',
        'SELECT 1'
    )
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 't_resource_runtime_policy'
      AND index_name = 'idx_runtime_policy_probe_strategy'
);
PREPARE stmt FROM @drop_duplicate_runtime_probe_idx;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
