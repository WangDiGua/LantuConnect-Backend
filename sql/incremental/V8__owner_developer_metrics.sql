-- Owner 维度统计：invoke 侧 usage_record 可归因资源；技能包下载独立埋点
ALTER TABLE t_usage_record
    ADD COLUMN resource_id BIGINT NULL COMMENT '网关 invoke 对应 t_resource.id（可空兼容历史行）'
        AFTER `type`;

CREATE INDEX idx_usage_record_owner_lookup
    ON t_usage_record (resource_id, create_time);

CREATE TABLE t_skill_pack_download_event
(
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    resource_id            BIGINT       NOT NULL,
    resource_type          VARCHAR(32)  NOT NULL DEFAULT 'skill',
    owner_user_id          BIGINT       NOT NULL COMMENT 't_resource.created_by（归因 owner）',
    downloader_user_id     BIGINT NULL COMMENT '登录用户；仅 Key 下载时可为空',
    downloader_api_key_id  VARCHAR(64) NULL COMMENT 't_api_key.id',
    create_time            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_spd_owner_time (owner_user_id, create_time),
    INDEX idx_spd_resource (resource_id)
) COMMENT '技能包下载埋点（不计入 t_call_log）';
