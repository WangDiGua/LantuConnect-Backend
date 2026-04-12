-- Skill 上下文化：废弃 agent_depends_skill / mcp_depends_skill；新增语义 skill_depends_mcp（from=skill, to=mcp）
-- 将历史 mcp_depends_skill（from=mcp, to=skill）翻转为 skill_depends_mcp
-- 与 ResourceRegistryServiceImpl / ResourceBindingClosureService 一致

START TRANSACTION;

-- 1) MCP→Skill 前置链 → Skill→MCP 绑定（方向翻转）
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

-- 2) Skill 扩展表：执行模式与类型收敛（列名仍兼容 hosted_system_prompt 等业务列）
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
