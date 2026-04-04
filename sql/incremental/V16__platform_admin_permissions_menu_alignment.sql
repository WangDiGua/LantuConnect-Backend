-- 超管 Casbin 权限：合并历史 granular（user/role/org/apikey/update）与前端菜单/ROLE_PERMISSIONS.platform_admin 使用的别名（agent:edit、role:manage、mcp:* 等），
-- 避免 /auth/me 仅下发库表权限时侧栏子项（角色/API Key/组织）消失。

UPDATE t_platform_role
SET permissions = '["agent:read","agent:view","agent:create","agent:update","agent:edit","agent:delete","agent:publish","agent:audit","skill:read","skill:view","skill:create","skill:update","skill:edit","skill:delete","skill:publish","skill:audit","mcp:view","mcp:create","mcp:edit","mcp:delete","mcp:publish","app:view","app:create","app:update","app:edit","app:delete","dataset:read","dataset:view","dataset:create","dataset:update","dataset:edit","dataset:delete","dataset:apply","provider:view","provider:manage","user:manage","user:read","user:create","user:update","user:delete","role:manage","role:read","role:create","role:update","role:delete","org:manage","org:read","org:create","org:update","org:delete","api-key:manage","apikey:read","apikey:create","apikey:delete","resource-grant:manage","grant-application:review","resource:audit","system:config","monitor:view","audit:manage","developer:portal","developer-application:review"]',
    update_time = NOW()
WHERE role_code = 'platform_admin';
