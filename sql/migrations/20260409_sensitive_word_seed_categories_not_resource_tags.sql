-- 历史演示种子曾误用 agent/skill/mcp/app/dataset 作为敏感词「分类」，与资源 Tag 混淆。
-- 将仅针对演示词条迁回敏感词业务固定字典（与 t_tag、资源 type 无关）。
-- 与 sql/incremental/V39__sensitive_word_seed_categories_not_resource_tags.sql 同内容（手工迁移镜像）。
UPDATE t_sensitive_word SET category = 'general' WHERE word = 'seed_demo_agent' AND category = 'agent';
UPDATE t_sensitive_word SET category = 'review' WHERE word = 'seed_demo_skill' AND category = 'skill';
UPDATE t_sensitive_word SET category = 'user_profile' WHERE word = 'seed_demo_mcp' AND category = 'mcp';
UPDATE t_sensitive_word SET category = 'announcement' WHERE word = 'seed_demo_app' AND category = 'app';
UPDATE t_sensitive_word SET category = 'other' WHERE word = 'seed_demo_dataset' AND category = 'dataset';
