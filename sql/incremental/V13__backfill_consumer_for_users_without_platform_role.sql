-- 任意在 t_user_role_rel 中尚无记录的用户，补挂 consumer，与自助注册行为一致（JWT /me 不再为 unassigned）。
-- 仅影响「无任何平台角色映射」的账号，不覆盖已有管理员/开发者等绑定。

INSERT INTO t_user_role_rel (user_id, role_id, create_time)
SELECT u.user_id, pr.id, NOW()
FROM t_user u
INNER JOIN t_platform_role pr ON pr.role_code = 'consumer'
WHERE (u.deleted IS NULL OR u.deleted = 0)
  AND NOT EXISTS (
    SELECT 1 FROM t_user_role_rel ur WHERE ur.user_id = u.user_id
  );
