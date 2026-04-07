-- Skill 统一为技能包/清单资源：清空历史「运行时 / MCP 挂载」扩展字段；移除 skill→MCP 关系种子。
-- 列仍保留以利于回滚与旧客户端；应用层不再写入（见 ResourceRegistryServiceImpl#upsertSkillExt）。

UPDATE t_resource_skill_ext
SET parent_resource_id = NULL,
    mode = NULL,
    display_template = NULL,
    max_concurrency = NULL;

DELETE FROM t_resource_relation WHERE relation_type = 'skill_child_of';
