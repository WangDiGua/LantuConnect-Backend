-- 终端用户(user)与开发者(developer)的 permissions JSON 与前端 UserRoleContext.ROLE_PERMISSIONS
-- 及 /auth/me → serverPermissions 对齐（V14 仅含 read 四元组；旧 seed 中 developer 不含 MCP / 菜单别名）。
--
-- user：补 catalog 浏览用 view 点，与静态壳一致，不改变 API 侧已用的 read/view 分权约定。
-- developer：五类资源工作台 + grant-application:review、developer:portal；保留 :read / :update / dataset:apply 以兼容历史调用。

UPDATE t_platform_role
SET permissions = '["agent:read","skill:read","app:view","dataset:read","agent:view","skill:view","dataset:view"]',
    update_time = NOW()
WHERE role_code = 'user';

UPDATE t_platform_role
SET permissions = '["agent:read","agent:view","agent:create","agent:edit","agent:update","agent:publish","skill:read","skill:view","skill:create","skill:edit","skill:update","skill:publish","mcp:view","mcp:create","mcp:edit","mcp:publish","app:view","app:create","app:edit","dataset:read","dataset:view","dataset:create","dataset:edit","dataset:apply","grant-application:review","developer:portal"]',
    update_time = NOW()
WHERE role_code = 'developer';
