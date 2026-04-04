-- 审核员 Casbin 权限与前端 ROLE_PERMISSIONS.reviewer、Grant/入驻/开发者门户菜单及只读用户目录对齐。
-- 说明：资源注册代管见 ResourceRegistryServiceImpl.requireManageableResource（reviewer 角色即视为可管理他人资源）。

UPDATE t_platform_role
SET permissions = '["agent:read","skill:read","app:view","dataset:read","agent:view","agent:create","agent:edit","agent:audit","skill:view","skill:create","skill:edit","skill:audit","mcp:view","mcp:create","mcp:edit","mcp:audit","app:create","app:edit","app:audit","dataset:view","dataset:create","dataset:edit","dataset:audit","resource:audit","resource-grant:manage","grant-application:review","developer-application:review","developer:portal","monitor:view","user:read"]',
    update_time = NOW()
WHERE role_code = 'reviewer';
