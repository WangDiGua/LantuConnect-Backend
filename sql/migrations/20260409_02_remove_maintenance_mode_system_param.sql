-- 目的：删除未消费的系统参数 maintenance_mode（无过滤器/网关读取，开启无效）
-- 幂等：重复执行安全（DELETE 无匹配行不影响）

DELETE FROM t_system_param WHERE `key` = 'maintenance_mode';

-- 校验：SELECT * FROM t_system_param WHERE `key` = 'maintenance_mode'; 期望 0 行
