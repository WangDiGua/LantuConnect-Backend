-- 审计日志保留天数为数值型，与前端 number 控件及实体语义一致
UPDATE `t_security_setting`
SET `type` = 'number'
WHERE `key` = 'audit_log_retention' AND (`type` = 'input' OR `type` IS NULL OR `type` = '');
