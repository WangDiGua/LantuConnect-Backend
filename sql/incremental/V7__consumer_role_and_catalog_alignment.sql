-- 消费者角色：与 GatewayUserPermissionService 目录类型校验一致（mcp 与 skill 共用 skill:read）。
INSERT INTO t_platform_role (role_code, role_name, description, permissions, is_system, user_count)
VALUES (
        'consumer',
        '消费者',
        '使用已上架五类资源（目录/Grant/个人 Key 等）；可申请开发者入驻及个人账号能力；mcp 与 skill 共用 skill:read；不含资源注册/发布与平台级审核',
        JSON_ARRAY('agent:read', 'skill:read', 'app:view', 'dataset:read'),
        1,
        0
        )
ON DUPLICATE KEY UPDATE description  = VALUES(description),
                        permissions   = VALUES(permissions),
                        update_time   = CURRENT_TIMESTAMP;
