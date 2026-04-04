-- 修复 V6/V8 在客户端字符集非 utf8mb4（或脚本被按错误编码解释）时执行，导致 COLUMN/TABLE COMMENT 中文被存成「?」的问题。
-- 本脚本应在 utf8mb4 连接下执行（JDBC URL 带 useUnicode/characterEncoding=UTF-8；mysql 客户端加 --default-character-set=utf8mb4）。

ALTER TABLE t_resource
    MODIFY COLUMN access_policy VARCHAR(32) NOT NULL DEFAULT 'grant_required'
        COMMENT 'grant_required=须 Grant；open_org=同部门/menu 内免 Grant；open_platform=租户内已认证 Key 免 Grant（仍校验 scope）';

ALTER TABLE t_usage_record
    MODIFY COLUMN resource_id BIGINT NULL
        COMMENT '网关 invoke 对应 t_resource.id（可空兼容历史行）';

ALTER TABLE t_skill_pack_download_event
    MODIFY COLUMN resource_id BIGINT NOT NULL COMMENT '技能包所属资源 t_resource.id',
    MODIFY COLUMN owner_user_id BIGINT NOT NULL COMMENT 't_resource.created_by（归因 owner）',
    MODIFY COLUMN downloader_user_id BIGINT NULL COMMENT '登录用户；仅 Key 下载时可为空',
    MODIFY COLUMN downloader_api_key_id VARCHAR(64) NULL COMMENT 't_api_key.id';

ALTER TABLE t_skill_pack_download_event COMMENT = '技能包下载埋点（不计入 t_call_log）';
