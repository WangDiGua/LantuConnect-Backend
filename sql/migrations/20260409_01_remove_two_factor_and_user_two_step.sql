-- 目的：移除未在登录链路实现的双因素相关数据（系统安全项 two_factor_auth + t_user.two_step）
-- DANGEROUS：DROP COLUMN 非幂等；执行前请备份
-- 回滚：从备份恢复库表，或手工 ADD COLUMN two_step、INSERT two_factor_auth 一行（需与应用旧版本配套）

DELETE FROM t_security_setting WHERE `key` = 'two_factor_auth';

ALTER TABLE t_user DROP COLUMN `two_step`;

-- 执行后校验（期望 0 行 / Empty set）：
-- SELECT COUNT(*) FROM t_security_setting WHERE `key` = 'two_factor_auth';
-- SHOW COLUMNS FROM t_user LIKE 'two_step';
