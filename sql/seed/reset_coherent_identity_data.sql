-- =============================================================================
-- 身份域数据归一化：清理历史杂乱 / 临时种子，使用户—部门—平台角色—卫星表互相对齐
--
-- 执行前请备份库。连接使用 utf8mb4（mysql --default-character-set=utf8mb4；Windows 用 cmd 重定向）。
--
-- 行为摘要：
--   1) 删除 90001–90099 种子用户及「演示-*」部门（若有）
--   2) 删除废账号 user_id=6（test002）及以其为锚的子表数据
--   3) 更新 id 1–4 核心账号：menu_id 与学院/中心一致，姓名与配额/通知可对上
--   4) 为 testuser(4) 绑定 consumer；插入 pending_dev(5) + 入驻工单（若不存在）
--   5) 同步配额 target_name、用量表里「学生」维度的 agent_name、平台角色 user_count
--
-- 口令：与 sql/lantu_connect.sql 示例相同 BCrypt；不可用请在应用内替换 password_hash。
--
-- =============================================================================

SET NAMES utf8mb4;

SET @demo_pwd := '$2a$12$e0tp9aC1xUXoDf7XNCID7OQ5vYfQDLyUAWVJ9aWhxVz7mypmfNX0i';

SET @dept_lzu  := 1;  -- 兰州大学（根）
SET @dept_cs   := 2;  -- 计算机学院
SET @dept_it   := 3;  -- 信息技术中心
SET @dept_math := 5;  -- 数学与统计学院

START TRANSACTION;

-- ---------------------------------------------------------------------------
-- A) 9 万号段种子 + 演示部门
-- ---------------------------------------------------------------------------
DELETE FROM t_developer_application WHERE user_id BETWEEN 90001 AND 90099;
DELETE FROM t_user_role_rel          WHERE user_id BETWEEN 90001 AND 90099;
DELETE FROM t_login_history          WHERE user_id BETWEEN 90001 AND 90099;
DELETE FROM t_favorite               WHERE user_id BETWEEN 90001 AND 90099;
DELETE FROM t_notification           WHERE user_id BETWEEN 90001 AND 90099;
DELETE FROM t_usage_record           WHERE user_id BETWEEN 90001 AND 90099;
DELETE FROM t_review_helpful_rel     WHERE user_id BETWEEN 90001 AND 90099;
DELETE FROM t_review                 WHERE user_id BETWEEN 90001 AND 90099;
DELETE FROM t_sandbox_session        WHERE owner_user_id BETWEEN 90001 AND 90099;
DELETE FROM t_user                   WHERE user_id BETWEEN 90001 AND 90099;

DELETE FROM t_org_menu
WHERE menu_name IN (
    '演示-智算技术与创新中心',
    '演示-计算机科学与技术学院',
    '演示-数学与统计学院'
);

-- ---------------------------------------------------------------------------
-- B) 废用户 test002 (user_id=6)
-- ---------------------------------------------------------------------------
DELETE FROM t_review_helpful_rel WHERE user_id = 6;
DELETE FROM t_review               WHERE user_id = 6;
DELETE FROM t_favorite             WHERE user_id = 6;
DELETE FROM t_login_history        WHERE user_id = 6;
DELETE FROM t_user_role_rel        WHERE user_id = 6;
DELETE FROM t_developer_application WHERE user_id = 6;
DELETE FROM t_usage_record         WHERE user_id = 6;
DELETE FROM t_notification         WHERE user_id = 6;
DELETE FROM t_sandbox_session      WHERE owner_user_id = 6;

UPDATE t_skill_pack_download_event
SET downloader_user_id = NULL
WHERE downloader_user_id = 6;

UPDATE t_resource SET created_by = 3 WHERE created_by = 6;
DELETE FROM t_user WHERE user_id = 6;

-- ---------------------------------------------------------------------------
-- C) 核心用户 1–4
-- ---------------------------------------------------------------------------
UPDATE t_user SET
    real_name   = '系统管理员',
    school_id   = @dept_lzu,
    menu_id     = @dept_it,
    major       = NULL,
    class       = NULL,
    role        = 99,
    mobile      = '13800138000',
    mail        = 'admin.sec@lzu.edu.cn',
    head_image  = NULL,
    status      = 'active',
    deleted     = 0,
    language    = 'zh-CN'
WHERE user_id = 1;

UPDATE t_user SET
    real_name   = '刘院务（计算机学院）',
    school_id   = @dept_lzu,
    menu_id     = @dept_cs,
    major       = NULL,
    class       = NULL,
    role        = 10,
    mobile      = '13800138001',
    mail        = 'liu.cs.admin@lzu.edu.cn',
    head_image  = NULL
WHERE user_id = 2;

UPDATE t_user SET
    real_name   = '王开发者',
    school_id   = @dept_lzu,
    menu_id     = @dept_cs,
    major       = '软件工程',
    class       = '软工2103',
    role        = 5,
    mobile      = '13800138002',
    mail        = 'wang.dev@stu.lzu.edu.cn',
    head_image  = NULL
WHERE user_id = 3;

UPDATE t_user SET
    real_name   = '李同学',
    school_id   = @dept_lzu,
    menu_id     = @dept_math,
    major       = '信息与计算科学',
    class       = '应数2102',
    role        = 1,
    mobile      = '13800138003',
    mail        = 'li.student@stu.lzu.edu.cn',
    head_image  = NULL
WHERE user_id = 4;

-- ---------------------------------------------------------------------------
-- D) 平台角色：1–3 保持原绑定；4 绑定 consumer（仅一条 consumer）
-- ---------------------------------------------------------------------------
DELETE FROM t_user_role_rel WHERE user_id = 4;

INSERT INTO t_user_role_rel (user_id, role_id)
SELECT 4, id FROM t_platform_role WHERE role_code = 'consumer' LIMIT 1;

-- 确保 1–3 绑正确（幂等）
DELETE FROM t_user_role_rel WHERE user_id IN (1, 2, 3);

INSERT INTO t_user_role_rel (user_id, role_id)
SELECT 1, id FROM t_platform_role WHERE role_code = 'platform_admin' LIMIT 1;

INSERT INTO t_user_role_rel (user_id, role_id)
SELECT 2, id FROM t_platform_role WHERE role_code = 'dept_admin' LIMIT 1;

INSERT INTO t_user_role_rel (user_id, role_id)
SELECT 3, id FROM t_platform_role WHERE role_code = 'developer' LIMIT 1;

-- ---------------------------------------------------------------------------
-- E) 待入驻账号 user_id=5（不占平台角色）
-- ---------------------------------------------------------------------------
DELETE FROM t_developer_application WHERE user_id = 5;
DELETE FROM t_user_role_rel        WHERE user_id = 5;
DELETE FROM t_favorite             WHERE user_id = 5;
DELETE FROM t_login_history        WHERE user_id = 5;
DELETE FROM t_usage_record         WHERE user_id = 5;
DELETE FROM t_notification         WHERE user_id = 5;
DELETE FROM t_user                 WHERE user_id = 5;

INSERT INTO t_user (
    user_id, username, password_hash, real_name, sex, school_id, menu_id, major, class, role,
    mobile, mail, status, deleted, language
) VALUES (
    5, 'pending_dev', @demo_pwd, '张实习', 1, @dept_lzu, @dept_cs,
    '计算机科学与技术', NULL, 0,
    '13800138005', 'zhang.pending@stu.lzu.edu.cn', 'active', 0, 'zh-CN'
);

INSERT INTO t_developer_application (
    user_id, contact_email, contact_phone, company_name, apply_reason, status
) VALUES (
    5,
    'zhang.pending@stu.lzu.edu.cn',
    '13800138005',
    '兰州大学计算机学院某课题组',
    '希望开通开发者权限：参与校园问答 Agent 与 Skill 共建，指导教师已线下确认。',
    'pending'
);

-- ---------------------------------------------------------------------------
-- F) 组织表与配额、通知、用量：展示一致化
-- ---------------------------------------------------------------------------
UPDATE t_org_menu SET head_count = 2600 WHERE menu_id = @dept_cs;
UPDATE t_org_menu SET head_count = 135  WHERE menu_id = @dept_it;

UPDATE t_quota SET target_name = '计算机学院'       WHERE target_type = 'department' AND target_id = @dept_cs;
UPDATE t_quota SET target_name = '信息技术中心'     WHERE target_type = 'department' AND target_id = @dept_it;
UPDATE t_quota SET target_name = '王开发者'         WHERE target_type = 'user'       AND target_id = 3;

INSERT INTO t_quota (target_type, target_id, target_name, daily_limit, monthly_limit, daily_used, monthly_used, enabled)
SELECT 'user', 4, '李同学', 300, 8000, 0, 120, 1
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM t_quota q WHERE q.target_type = 'user' AND q.target_id = 4
);

UPDATE t_notification
SET title = '本月调用配额提示（演示）',
    body  = '李同学：当前账号已配置个人配额，可在控制台查看用量；如需调整请联系本院管理员。'
WHERE user_id = 4 AND source_type = 'quota';

UPDATE t_platform_role r
SET user_count = (SELECT COUNT(*) FROM t_user_role_rel ur WHERE ur.role_id = r.id);

COMMIT;

SET @next_uid := (SELECT IFNULL(MAX(user_id), 0) + 1 FROM t_user);
SET @sql_alter := CONCAT('ALTER TABLE t_user AUTO_INCREMENT = ', @next_uid);
PREPARE stmt_uid FROM @sql_alter;
EXECUTE stmt_uid;
DEALLOCATE PREPARE stmt_uid;

SELECT 'reset_coherent_identity_data done' AS message,
       (SELECT GROUP_CONCAT(CONCAT(user_id,':',username) ORDER BY user_id) FROM t_user WHERE user_id <= 7) AS users;
