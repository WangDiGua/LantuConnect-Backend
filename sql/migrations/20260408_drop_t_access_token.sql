-- 移除未落地的「访问令牌台账」表 t_access_token（用户管理 /tokens 功能已下线）。
-- 幂等：可重复执行。

DROP TABLE IF EXISTS `t_access_token`;
