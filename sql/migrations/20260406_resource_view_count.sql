-- 资源详情浏览量：列表展示 + GET /catalog/resources/{type}/{id} 成功返回后自增
-- 执行前请备份；若列已存在可跳过（MySQL 8.0.29+ 可用 IF NOT EXISTS）

ALTER TABLE t_resource
    ADD COLUMN view_count BIGINT NOT NULL DEFAULT 0 COMMENT '资源详情累计浏览量' AFTER access_policy;
