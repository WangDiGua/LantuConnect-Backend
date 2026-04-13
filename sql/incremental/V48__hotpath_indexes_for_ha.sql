-- 高可用热路径索引
-- 目标：降低 API Key 认证、Agent 关系展开的热点查询开销

ALTER TABLE `t_api_key`
    ADD INDEX `idx_api_key_key_hash` (`key_hash`);

ALTER TABLE `t_resource_relation`
    ADD INDEX `idx_relation_from_type_to` (`from_resource_id`, `relation_type`, `to_resource_id`),
    ADD INDEX `idx_relation_to_type_from` (`to_resource_id`, `relation_type`, `from_resource_id`);
