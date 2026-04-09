-- 优化 mcp_depends_skill 逆查（to_resource_id + relation_type）；若已手动创建同名索引则跳过
SET @db := DATABASE();
SET @idx_exists := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = @db AND table_name = 't_resource_relation' AND index_name = 'idx_relation_to_type');
SET @ddl := IF(@idx_exists = 0,
    'CREATE INDEX idx_relation_to_type ON t_resource_relation (to_resource_id, relation_type)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
