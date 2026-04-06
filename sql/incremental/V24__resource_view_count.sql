-- 市场浏览量：与 sql/migrations/20260406_resource_view_count.sql 同内容（Flyway 增量）
ALTER TABLE t_resource
    ADD COLUMN view_count BIGINT NOT NULL DEFAULT 0 COMMENT '资源详情累计浏览量' AFTER access_policy;
