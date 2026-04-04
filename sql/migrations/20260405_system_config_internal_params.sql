-- System config internal params (insert if missing). ASCII descriptions avoid client charset issues.
INSERT INTO t_system_param (`key`, `value`, `type`, `description`, `category`, `editable`, `update_time`)
SELECT 'admin_network_allowlist',
       '["10.0.0.0/8","172.16.0.0/12"]',
       'json',
       'Admin console IP allowlist (JSON string array of CIDR)',
       'security',
       1,
       NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM t_system_param WHERE `key` = 'admin_network_allowlist' LIMIT 1);

INSERT INTO t_system_param (`key`, `value`, `type`, `description`, `category`, `editable`, `update_time`)
SELECT 'api_path_acl_rules',
       '[]',
       'json',
       'API path ACL rules JSON array [{id,path,roles}]',
       'security',
       1,
       NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM t_system_param WHERE `key` = 'api_path_acl_rules' LIMIT 1);
