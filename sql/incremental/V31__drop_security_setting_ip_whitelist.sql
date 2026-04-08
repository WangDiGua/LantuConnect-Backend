-- 与 admin_network_allowlist 重复且未使用的安全设置项
DELETE FROM t_security_setting WHERE `key` = 'ip_whitelist';
