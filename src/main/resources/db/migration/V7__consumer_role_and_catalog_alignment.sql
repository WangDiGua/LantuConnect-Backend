-- 消费者角色：与 GatewayUserPermissionService 目录类型校验一致（mcp 与 skill 共用 skill:read）。
INSERT INTO t_platform_role (role_code, role_name, description, permissions, is_system, user_count)
VALUES (
        'consumer',
        '消费者',
        '目录与市场只读（五类资源浏览所需 Casbin 权限；不含注册/发布/审核）',
        JSON_ARRAY('agent:read', 'skill:read', 'app:view', 'dataset:read'),
        1,
        0
        )
ON DUPLICATE KEY UPDATE description  = VALUES(description),
                        permissions   = VALUES(permissions),
                        update_time   = CURRENT_TIMESTAMP;
