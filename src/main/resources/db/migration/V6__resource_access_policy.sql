-- 资源消费策略（与 per-resource Grant 配合；网关短路逻辑见后续阶段）
ALTER TABLE t_resource
    ADD COLUMN access_policy VARCHAR(32) NOT NULL DEFAULT 'grant_required'
        COMMENT 'grant_required=须 Grant；open_org=同部门/menu 内免 Grant；open_platform=租户内已认证 Key 免 Grant（仍校验 scope）'
        AFTER category_id;
