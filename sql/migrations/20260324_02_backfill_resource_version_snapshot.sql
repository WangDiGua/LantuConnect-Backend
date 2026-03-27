-- =============================================================================
-- Migration: 20260324_02_backfill_resource_version_snapshot.sql
-- Purpose  : Backfill snapshot_json for active resource versions.
-- Notes    : Idempotent (updates only when snapshot_json IS NULL).
-- =============================================================================

SET NAMES utf8mb4;

-- Agent snapshots
UPDATE t_resource_version rv
JOIN t_resource r ON r.id = rv.resource_id
JOIN t_resource_agent_ext ae ON ae.resource_id = r.id
SET rv.snapshot_json = JSON_OBJECT(
    'resourceCode', r.resource_code,
    'displayName', r.display_name,
    'status', r.status,
    'invokeType', COALESCE(JSON_UNQUOTE(JSON_EXTRACT(ae.spec_json, '$.protocol')), 'rest'),
    'endpoint', JSON_UNQUOTE(JSON_EXTRACT(ae.spec_json, '$.url')),
    'spec', ae.spec_json
)
WHERE rv.status = 'active'
  AND rv.snapshot_json IS NULL
  AND r.deleted = 0
  AND r.resource_type = 'agent';

-- Skill snapshots (non-MCP)
UPDATE t_resource_version rv
JOIN t_resource r ON r.id = rv.resource_id
JOIN t_resource_skill_ext se ON se.resource_id = r.id
SET rv.snapshot_json = JSON_OBJECT(
    'resourceCode', r.resource_code,
    'displayName', r.display_name,
    'status', r.status,
    'invokeType', COALESCE(JSON_UNQUOTE(JSON_EXTRACT(se.spec_json, '$.protocol')), 'rest'),
    'endpoint', JSON_UNQUOTE(JSON_EXTRACT(se.spec_json, '$.url')),
    'spec', se.spec_json
)
WHERE rv.status = 'active'
  AND rv.snapshot_json IS NULL
  AND r.deleted = 0
  AND r.resource_type = 'skill'
  AND (se.skill_type IS NULL OR se.skill_type <> 'mcp');

-- MCP snapshots from mcp table
UPDATE t_resource_version rv
JOIN t_resource r ON r.id = rv.resource_id
JOIN t_resource_mcp_ext me ON me.resource_id = r.id
SET rv.snapshot_json = JSON_OBJECT(
    'resourceCode', r.resource_code,
    'displayName', r.display_name,
    'status', r.status,
    'invokeType', COALESCE(me.protocol, 'mcp'),
    'endpoint', me.endpoint,
    'spec', me.auth_config
)
WHERE rv.status = 'active'
  AND rv.snapshot_json IS NULL
  AND r.deleted = 0
  AND r.resource_type = 'mcp';

-- MCP snapshots from skill-type MCP rows (normalized path)
UPDATE t_resource_version rv
JOIN t_resource r ON r.id = rv.resource_id
JOIN t_resource_skill_ext se ON se.resource_id = r.id
SET rv.snapshot_json = JSON_OBJECT(
    'resourceCode', r.resource_code,
    'displayName', r.display_name,
    'status', r.status,
    'invokeType', 'mcp',
    'endpoint', JSON_UNQUOTE(JSON_EXTRACT(se.spec_json, '$.url')),
    'spec', se.spec_json
)
WHERE rv.status = 'active'
  AND rv.snapshot_json IS NULL
  AND r.deleted = 0
  AND r.resource_type = 'skill'
  AND se.skill_type = 'mcp';

-- App snapshots
UPDATE t_resource_version rv
JOIN t_resource r ON r.id = rv.resource_id
JOIN t_resource_app_ext ae ON ae.resource_id = r.id
SET rv.snapshot_json = JSON_OBJECT(
    'resourceCode', r.resource_code,
    'displayName', r.display_name,
    'status', r.status,
    'invokeType', 'redirect',
    'endpoint', ae.app_url,
    'spec', JSON_OBJECT('embedType', ae.embed_type)
)
WHERE rv.status = 'active'
  AND rv.snapshot_json IS NULL
  AND r.deleted = 0
  AND r.resource_type = 'app';

-- Dataset snapshots
UPDATE t_resource_version rv
JOIN t_resource r ON r.id = rv.resource_id
JOIN t_resource_dataset_ext de ON de.resource_id = r.id
SET rv.snapshot_json = JSON_OBJECT(
    'resourceCode', r.resource_code,
    'displayName', r.display_name,
    'status', r.status,
    'invokeType', 'metadata',
    'spec', JSON_OBJECT(
      'dataType', de.data_type,
      'format', de.format,
      'recordCount', de.record_count,
      'fileSize', de.file_size,
      'tags', de.tags
    )
)
WHERE rv.status = 'active'
  AND rv.snapshot_json IS NULL
  AND r.deleted = 0
  AND r.resource_type = 'dataset';

-- Verification
SELECT COUNT(*) AS empty_snapshot_count
FROM t_resource_version
WHERE status = 'active' AND snapshot_json IS NULL;
