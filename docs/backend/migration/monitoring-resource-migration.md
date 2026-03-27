# 健康与熔断迁移记录

## 目标
- 将健康检查与熔断配置从旧资源标识（`agent_name`）迁移到统一资源模型（`t_resource*`）。
- 在统一网关调用路径上接入熔断判定与失败自动开断。

## 已完成
- 监控治理迁移能力已并入当前数据库基线（`sql/lantu_connect.sql`），后续增量按 `sql/migrations/README.md` 执行：
  - 新建 `t_resource_health_config`
  - 新建 `t_resource_circuit_breaker`
  - 从 `t_health_config` / `t_circuit_breaker` 迁移历史数据（通过 `resource_code` 关联 `t_resource`）
- 监控实体切换到新表（保持接口字段兼容）：
  - `HealthConfig` -> `t_resource_health_config`
  - `CircuitBreaker` -> `t_resource_circuit_breaker`
- 健康检查定时任务切换新表：
  - `HealthCheckTask` 改为读写 `t_resource_health_config`
- 监控服务增强资源校验：
  - `HealthServiceImpl` 在保存配置/手工熔断前校验目标资源存在，并同步 `resource_id/resource_type/display_name`
- 统一网关接入熔断：
  - `UnifiedGatewayServiceImpl.invoke` 调用前检查 `OPEN` 状态并快速失败
  - 调用后按成功/失败更新熔断统计，失败达到阈值自动开断，成功自动关闭并清零失败计数

## 验证结果
- 编译通过：`./mvnw.cmd -DskipTests compile`
- 新表迁移行数：
  - `t_resource_health_config`: 3
  - `t_resource_circuit_breaker`: 2
