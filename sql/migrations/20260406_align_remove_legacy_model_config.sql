-- 与本项目 V18 一致：产品已移除「大模型 / 模型配置」遗留表与系统参数（幂等）。
DELETE FROM `t_system_param` WHERE `key` = 'default_model_id';
DROP TABLE IF EXISTS `t_model_config`;
