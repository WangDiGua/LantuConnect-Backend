-- 与 sql/incremental/V25__sensitive_action_audit.sql 同内容（手工迁移镜像）
-- 敏感操作审计（如 API Key 撤销强校验）
CREATE TABLE IF NOT EXISTS t_sensitive_action_audit (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '操作用户',
    action_type VARCHAR(64) NOT NULL COMMENT '动作类型如 api_key_revoke',
    target_id VARCHAR(64) NULL COMMENT '目标标识如 api key id',
    success TINYINT NOT NULL DEFAULT 0 COMMENT '1=成功 0=失败',
    fail_reason VARCHAR(512) NULL COMMENT '失败原因',
    client_ip VARCHAR(64) NULL COMMENT '客户端 IP',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='敏感操作审计';
