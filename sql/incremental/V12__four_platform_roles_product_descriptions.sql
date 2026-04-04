-- 四类平台角色文案与产品定义对齐（权限 JSON 仍以各角色既有 Casbin 条目为准，本脚本仅更新说明）。

UPDATE t_platform_role
SET role_name   = '平台管理员',
    description = '管理整个平台：全局用户、组织与角色、系统参数与集成、全平台资源生命周期与跨部门审核等。',
    update_time = CURRENT_TIMESTAMP
WHERE role_code = 'platform_admin';

UPDATE t_platform_role
SET role_name   = '部门管理员',
    description = '管理本部门范围：本院系开发者与消费者、本部门相关资源的协同与审核、Grant 工单在同部门策略内的处理等。',
    update_time = CURRENT_TIMESTAMP
WHERE role_code = 'dept_admin';

UPDATE t_platform_role
SET role_name   = '开发者',
    description = '注册与维护五类资源（Agent/Skill/MCP/App/Dataset），提交审核与发布迭代，处理与自身资源相关的审核与工单；不承担纯消费者侧「仅使用他人资源」的默认职责（由消费者角色承担）。',
    update_time = CURRENT_TIMESTAMP
WHERE role_code = 'developer';

UPDATE t_platform_role
SET role_name   = '消费者',
    description = '使用已上架的五类资源：浏览目录、申请授权（Grant）、管理个人 API Key 并完成约定方式的调用；可申请开发者入驻；可使用个人资料、安全设置与登录历史等；不含资源注册、发布与平台级审核。',
    update_time = CURRENT_TIMESTAMP
WHERE role_code = 'consumer';
