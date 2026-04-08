-- 与安全设置页重复的「IP 白名单」项：从未接入后端逻辑；管理端 CIDR 以 t_system_param.admin_network_allowlist +「网络配置」为准。
-- 幂等。

DELETE FROM t_security_setting WHERE `key` = 'ip_whitelist';
