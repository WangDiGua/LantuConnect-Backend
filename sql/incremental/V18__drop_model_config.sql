-- 产品移除「大模型 / 模型配置」：删表与遗留系统参数（幂等）。
DROP TABLE IF EXISTS `t_model_config`;
DELETE FROM `t_system_param` WHERE `key` = 'default_model_id';
