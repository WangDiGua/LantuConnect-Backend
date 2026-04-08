-- 剔除短信验证码存储；登录与敏感操作不再使用短信能力。
DROP TABLE IF EXISTS `t_sms_verify_code`;
