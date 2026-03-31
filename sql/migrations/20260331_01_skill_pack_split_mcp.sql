-- =============================================================================
-- Migration: 20260331_01_skill_pack_split_mcp.sql
-- Purpose  : Skill 收窄为 Anthropic 式内容包；原 skill+mcp/http_api 迁为 resourceType=mcp。
-- IMPORTANT: 执行前请备份数据库；建议停写后先迁数据再部署新代码。
-- =============================================================================

SET NAMES utf8mb4;

-- 1) skill 扩展表：内容包字段 + spec_json 可空
ALTER TABLE t_resource_skill_ext
    ADD COLUMN IF NOT EXISTS artifact_uri VARCHAR(1024) NULL COMMENT '技能包 URI（上传/外链）' AFTER skill_type,
    ADD COLUMN IF NOT EXISTS artifact_sha256 CHAR(64) NULL COMMENT '包摘要 hex' AFTER artifact_uri,
    ADD COLUMN IF NOT EXISTS manifest_json JSON NULL COMMENT '包 manifest（JSON）' AFTER artifact_sha256,
    ADD COLUMN IF NOT EXISTS entry_doc VARCHAR(256) NULL COMMENT '入口文档相对路径，如 SKILL.md' AFTER manifest_json;

ALTER TABLE t_resource_skill_ext
    MODIFY COLUMN spec_json JSON NULL COMMENT '可选附加元数据（非远程调用配置）';

-- 2) 将「可远程调用」的旧 skill 迁入 mcp（skill_type 为 mcp / http_api）
INSERT INTO t_resource_mcp_ext (resource_id, endpoint, protocol, auth_type, auth_config)
SELECT se.resource_id,
       NULLIF(TRIM(JSON_UNQUOTE(JSON_EXTRACT(se.spec_json, '$.url'))), ''),
       CASE WHEN LOWER(se.skill_type) = 'http_api' THEN 'http' ELSE 'mcp' END,
       'none',
       se.spec_json
FROM t_resource_skill_ext se
         INNER JOIN t_resource r ON r.id = se.resource_id AND r.deleted = 0 AND r.resource_type = 'skill'
WHERE LOWER(TRIM(se.skill_type)) IN ('mcp', 'http_api')
ON DUPLICATE KEY UPDATE endpoint    = VALUES(endpoint),
                        protocol    = VALUES(protocol),
                        auth_type   = VALUES(auth_type),
                        auth_config = VALUES(auth_config);

UPDATE t_resource r
    INNER JOIN t_resource_skill_ext se ON se.resource_id = r.id
SET r.resource_type = 'mcp'
WHERE r.resource_type = 'skill'
  AND LOWER(TRIM(se.skill_type)) IN ('mcp', 'http_api');

-- 删除已转为 mcp 的 skill 扩展行（mcp 仅使用 t_resource_mcp_ext）
DELETE se
FROM t_resource_skill_ext se
         INNER JOIN t_resource r ON r.id = se.resource_id
WHERE r.resource_type = 'mcp';

-- 3) 卫星表：标签与审核项中的资源类型
UPDATE t_resource_tag_rel tr
    INNER JOIN t_resource r ON r.id = tr.resource_id AND r.deleted = 0 AND r.resource_type = 'mcp'
SET tr.resource_type = 'mcp'
WHERE tr.resource_type = 'skill';

UPDATE t_audit_item ai
    INNER JOIN t_resource r ON r.id = ai.target_id AND r.deleted = 0 AND r.resource_type = 'mcp'
SET ai.target_type = 'mcp'
WHERE ai.target_type = 'skill';

-- 4) 版本快照与 mcp 扩展一致（凡 resource_type=mcp）
UPDATE t_resource_version rv
    INNER JOIN t_resource r ON r.id = rv.resource_id AND r.deleted = 0 AND r.resource_type = 'mcp'
    INNER JOIN t_resource_mcp_ext me ON me.resource_id = r.id
SET rv.snapshot_json = JSON_OBJECT(
        'resourceType', 'mcp',
        'resourceCode', r.resource_code,
        'displayName', r.display_name,
        'description', r.description,
        'status', r.status,
        'invokeType', COALESCE(NULLIF(TRIM(me.protocol), ''), 'mcp'),
        'endpoint', me.endpoint,
        'spec', me.auth_config
    )
WHERE rv.status = 'active';

-- 5) 内容包型 skill 的版本快照
UPDATE t_resource_version rv
    INNER JOIN t_resource_skill_ext se ON se.resource_id = rv.resource_id
    INNER JOIN t_resource r ON r.id = rv.resource_id AND r.deleted = 0 AND r.resource_type = 'skill'
SET rv.snapshot_json = JSON_OBJECT(
        'resourceType', 'skill',
        'packFormat', se.skill_type,
        'resourceCode', r.resource_code,
        'displayName', r.display_name,
        'description', r.description,
        'status', r.status,
        'invokeType', 'artifact',
        'endpoint', se.artifact_uri,
        'spec', JSON_OBJECT(
                'manifest', se.manifest_json,
                'entryDoc', se.entry_doc,
                'extra', se.spec_json
            )
    )
WHERE rv.status = 'active';
