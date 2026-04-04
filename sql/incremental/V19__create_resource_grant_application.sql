-- 资源授权申请工单（与 GrantApplicationController / ResourceGrantApplicationMapper 一致）
CREATE TABLE IF NOT EXISTS t_resource_grant_application (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    applicant_id    BIGINT       NOT NULL COMMENT '申请人 user_id',
    resource_type   VARCHAR(32)  NOT NULL COMMENT '资源类型: agent/skill/mcp/app/dataset',
    resource_id     BIGINT       NOT NULL COMMENT '目标资源 ID',
    api_key_id      VARCHAR(64)  NOT NULL COMMENT '申请绑定的 API Key ID',
    actions         JSON         NOT NULL COMMENT '申请的操作权限: ["catalog","resolve","invoke"]',
    use_case        VARCHAR(512) DEFAULT NULL COMMENT '使用场景说明',
    call_frequency  VARCHAR(128) DEFAULT NULL COMMENT '预估调用频次',
    status          VARCHAR(32)  NOT NULL DEFAULT 'pending' COMMENT 'pending/approved/rejected',
    reviewer_id     BIGINT       DEFAULT NULL COMMENT '审批人 user_id',
    reject_reason   VARCHAR(512) DEFAULT NULL COMMENT '驳回原因',
    review_time     DATETIME     DEFAULT NULL COMMENT '审批时间',
    expires_at      DATETIME     DEFAULT NULL COMMENT '申请的授权过期时间（可选）',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_applicant (applicant_id),
    INDEX idx_resource (resource_type, resource_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='资源授权申请工单';
