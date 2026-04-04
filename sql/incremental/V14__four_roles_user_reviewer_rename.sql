-- 产品四类角色落库：用户(user)、开发者(developer)、审核员(reviewer)、平台超管(platform_admin)
-- 1) consumer → user（自助注册默认角色）
-- 2) dept_admin → reviewer（全平台审核，不再按部门隔离）

UPDATE t_platform_role
SET role_code = 'user',
    role_name = '用户',
    description = '登录平台，使用已发布上线的五类资源（目录、授权 Grant、个人 API Key 等）；可申请成为开发者；不含资源登记、发布、审核与平台治理。',
    permissions = '["agent:read","skill:read","app:view","dataset:read"]',
    update_time = NOW()
WHERE role_code = 'consumer';

UPDATE t_platform_role
SET role_code = 'reviewer',
    role_name = '审核员',
    description = '在全平台范围内审核开发者提交的资源与关联工单（不按部门隔离）；可代管 Grant/授权类审批与开发者入驻审批所需操作；不含平台全局参数与用户组织治理（由平台超管负责）。',
    permissions = '["agent:read","skill:read","app:view","dataset:read","agent:audit","skill:audit","mcp:audit","app:audit","dataset:audit","monitor:view","user:read"]',
    update_time = NOW()
WHERE role_code = 'dept_admin';

UPDATE t_platform_role
SET role_name = '平台超管',
    description = '平台级配置、运维、监控与安全；全局用户/角色/组织治理；全量审核与资源生命周期终局操作。',
    update_time = NOW()
WHERE role_code = 'platform_admin';
