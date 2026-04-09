-- 系统配置演示数据：五类资源 t_tag + 敏感词示例。
-- 敏感词「分类」使用敏感词业务固定字典（非资源 type）；与 t_tag.category 无关。
INSERT INTO t_tag (name, category, usage_count, create_time)
SELECT '应用', 'app', 0, NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM t_tag WHERE category = 'app' LIMIT 1);

INSERT INTO t_sensitive_word (word, category, severity, source, enabled)
SELECT * FROM (SELECT 'seed_demo_agent' AS word, 'general' AS category, 1 AS severity, 'seed' AS source, 1 AS enabled) t
WHERE NOT EXISTS (SELECT 1 FROM t_sensitive_word WHERE word = 'seed_demo_agent');

INSERT INTO t_sensitive_word (word, category, severity, source, enabled)
SELECT * FROM (SELECT 'seed_demo_skill' AS word, 'review' AS category, 1 AS severity, 'seed' AS source, 1 AS enabled) t
WHERE NOT EXISTS (SELECT 1 FROM t_sensitive_word WHERE word = 'seed_demo_skill');

INSERT INTO t_sensitive_word (word, category, severity, source, enabled)
SELECT * FROM (SELECT 'seed_demo_mcp' AS word, 'user_profile' AS category, 1 AS severity, 'seed' AS source, 1 AS enabled) t
WHERE NOT EXISTS (SELECT 1 FROM t_sensitive_word WHERE word = 'seed_demo_mcp');

INSERT INTO t_sensitive_word (word, category, severity, source, enabled)
SELECT * FROM (SELECT 'seed_demo_app' AS word, 'announcement' AS category, 1 AS severity, 'seed' AS source, 1 AS enabled) t
WHERE NOT EXISTS (SELECT 1 FROM t_sensitive_word WHERE word = 'seed_demo_app');

INSERT INTO t_sensitive_word (word, category, severity, source, enabled)
SELECT * FROM (SELECT 'seed_demo_dataset' AS word, 'other' AS category, 1 AS severity, 'seed' AS source, 1 AS enabled) t
WHERE NOT EXISTS (SELECT 1 FROM t_sensitive_word WHERE word = 'seed_demo_dataset');

INSERT INTO t_sensitive_word (word, category, severity, source, enabled)
SELECT * FROM (SELECT 'seed_demo_general' AS word, 'default' AS category, 1 AS severity, 'seed' AS source, 1 AS enabled) t
WHERE NOT EXISTS (SELECT 1 FROM t_sensitive_word WHERE word = 'seed_demo_general');
