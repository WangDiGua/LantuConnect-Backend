-- Skill 上下文化：关系翻转与 skill_ext 默认值（与 sql/migrations/20260412_skill_context_relation_and_defaults.sql 相同）

START TRANSACTION;

INSERT INTO t_resource_relation (from_resource_id, to_resource_id, relation_type, create_time)
SELECT r.to_resource_id, r.from_resource_id, 'skill_depends_mcp', COALESCE(r.create_time, NOW())
FROM t_resource_relation r
WHERE r.relation_type = 'mcp_depends_skill'
  AND NOT EXISTS (
    SELECT 1 FROM t_resource_relation x
    WHERE x.from_resource_id = r.to_resource_id
      AND x.to_resource_id = r.from_resource_id
      AND x.relation_type = 'skill_depends_mcp'
  );

DELETE FROM t_resource_relation WHERE relation_type = 'mcp_depends_skill';
DELETE FROM t_resource_relation WHERE relation_type = 'agent_depends_skill';

UPDATE t_resource_skill_ext
SET execution_mode = 'context'
WHERE execution_mode IN ('hosted', 'pack');

UPDATE t_resource_skill_ext
SET skill_type = 'context_v1'
WHERE LOWER(skill_type) IN ('hosted_v1', 'hosted');

ALTER TABLE t_resource_skill_ext
  MODIFY COLUMN skill_type VARCHAR(16) NOT NULL COMMENT 'e.g. context_v1',
  MODIFY COLUMN execution_mode VARCHAR(16) NOT NULL DEFAULT 'context' COMMENT 'context skill spec; zip pack removed';

COMMIT;
