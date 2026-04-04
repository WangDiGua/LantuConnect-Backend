-- 系统配置演示数据：五类资源标签全覆盖 + 按资源维度示例敏感词（词本身为无害占位，便于超管页有内容可筛选）
INSERT INTO t_tag (name, category, usage_count, create_time)
SELECT '应用', 'app', 0, NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM t_tag WHERE category = 'app' LIMIT 1);

INSERT INTO t_sensitive_word (word, category, severity, source, enabled)
SELECT * FROM (SELECT 'seed_demo_agent' AS word, 'agent' AS category, 1 AS severity, 'seed' AS source, 1 AS enabled) t
WHERE NOT EXISTS (SELECT 1 FROM t_sensitive_word WHERE word = 'seed_demo_agent');

INSERT INTO t_sensitive_word (word, category, severity, source, enabled)
SELECT * FROM (SELECT 'seed_demo_skill' AS word, 'skill' AS category, 1 AS severity, 'seed' AS source, 1 AS enabled) t
WHERE NOT EXISTS (SELECT 1 FROM t_sensitive_word WHERE word = 'seed_demo_skill');

INSERT INTO t_sensitive_word (word, category, severity, source, enabled)
SELECT * FROM (SELECT 'seed_demo_mcp' AS word, 'mcp' AS category, 1 AS severity, 'seed' AS source, 1 AS enabled) t
WHERE NOT EXISTS (SELECT 1 FROM t_sensitive_word WHERE word = 'seed_demo_mcp');

INSERT INTO t_sensitive_word (word, category, severity, source, enabled)
SELECT * FROM (SELECT 'seed_demo_app' AS word, 'app' AS category, 1 AS severity, 'seed' AS source, 1 AS enabled) t
WHERE NOT EXISTS (SELECT 1 FROM t_sensitive_word WHERE word = 'seed_demo_app');

INSERT INTO t_sensitive_word (word, category, severity, source, enabled)
SELECT * FROM (SELECT 'seed_demo_dataset' AS word, 'dataset' AS category, 1 AS severity, 'seed' AS source, 1 AS enabled) t
WHERE NOT EXISTS (SELECT 1 FROM t_sensitive_word WHERE word = 'seed_demo_dataset');

INSERT INTO t_sensitive_word (word, category, severity, source, enabled)
SELECT * FROM (SELECT 'seed_demo_general' AS word, 'general' AS category, 1 AS severity, 'seed' AS source, 1 AS enabled) t
WHERE NOT EXISTS (SELECT 1 FROM t_sensitive_word WHERE word = 'seed_demo_general');
