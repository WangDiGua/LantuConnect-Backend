-- 修复 consumer 角色在错误字符集客户端下写入导致的 role_name/description 乱码。
-- 角色本身由 V7 引入（非重复角色）：供仅浏览目录/市场、无发布与审核权限的终端用户。

UPDATE t_platform_role
SET role_name   = '消费者',
    description = '目录与市场只读（五类资源浏览所需 Casbin 权限；mcp 与 skill 共用 skill:read；不含注册/发布/审核）',
    update_time = CURRENT_TIMESTAMP
WHERE role_code = 'consumer';
