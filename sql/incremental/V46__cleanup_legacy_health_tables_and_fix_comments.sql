-- 修复新表中文注释，并清理已完成迁移的旧治理表

ALTER TABLE `t_resource_runtime_policy`
    COMMENT = '资源运行治理策略（健康检查 + 熔断）';

ALTER TABLE `t_resource_common_ext`
    COMMENT = '资源公共扩展字段';

DROP TABLE IF EXISTS `t_resource_health_config`;
DROP TABLE IF EXISTS `t_resource_circuit_breaker`;
