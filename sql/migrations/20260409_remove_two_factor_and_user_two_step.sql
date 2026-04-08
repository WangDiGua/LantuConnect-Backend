-- 双因素认证未在登录链路实现；移除系统安全项与用户侧 two_step 列
DELETE FROM t_security_setting WHERE `key` = 'two_factor_auth';

ALTER TABLE t_user DROP COLUMN `two_step`;
