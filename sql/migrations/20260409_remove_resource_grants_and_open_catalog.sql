-- 下线资源级 Grant / 授权工单；上架资源对满足 scope 的 API Key 开放调用。
-- 幂等：可重复执行。

UPDATE `t_resource` SET `access_policy` = 'open_platform' WHERE `deleted` = 0;

DROP TABLE IF EXISTS `t_resource_invoke_grant`;
DROP TABLE IF EXISTS `t_resource_grant_application`;
