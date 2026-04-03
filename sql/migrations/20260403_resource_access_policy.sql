-- 与 Flyway V6 等价，便于未启用 Flyway 的环境手工执行
ALTER TABLE t_resource
    ADD COLUMN access_policy VARCHAR(32) NOT NULL DEFAULT 'grant_required'
        COMMENT 'grant_required | open_org | open_platform'
        AFTER category_id;
