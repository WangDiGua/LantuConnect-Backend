# 缺口补全最终记录

## 本轮补全目标
- 补齐“写双写”缺口（新旧模型并写）。
- 补齐“协议适配层”缺口（REST/MCP/OpenAPI/Webhook/SSE）。

## 1) 写双写补全

### 健康与熔断配置写双写
- 文件：`src/main/java/com/lantu/connect/monitoring/service/impl/HealthServiceImpl.java`
- 已实现：
  - 新表写入：`t_resource_health_config` / `t_resource_circuit_breaker`
  - 旧表同步：`t_health_config` / `t_circuit_breaker`
  - 删除健康配置时同时删除旧表对应记录

### 定时任务写双写
- 文件：
  - `src/main/java/com/lantu/connect/task/HealthCheckTask.java`
  - `src/main/java/com/lantu/connect/task/CircuitBreakerStateTask.java`
- 已实现：
  - 健康探测状态更新时同步回写旧健康表
  - 熔断状态 OPEN->HALF_OPEN 迁移时同步回写旧熔断表

### 网关运行态熔断统计双写
- 文件：`src/main/java/com/lantu/connect/gateway/service/impl/UnifiedGatewayServiceImpl.java`
- 已实现：
  - 调用成功/失败后的熔断统计先写新表，再同步旧熔断表
  - 兼容回滚场景的历史治理数据连续性

## 2) 协议适配层补全

### 新增协议调用抽象
- `src/main/java/com/lantu/connect/gateway/protocol/GatewayProtocolInvoker.java`
- `src/main/java/com/lantu/connect/gateway/protocol/ProtocolInvokerRegistry.java`
- `src/main/java/com/lantu/connect/gateway/protocol/ProtocolInvokeResult.java`

### 新增协议实现
- `HttpJsonProtocolInvoker`：支持 `http/rest/openapi/mcp/webhook`
- `SseProtocolInvoker`：支持 `sse`

### 网关接入协议路由
- 文件：`src/main/java/com/lantu/connect/gateway/service/impl/UnifiedGatewayServiceImpl.java`
- 已实现：
  - `invoke` 按 `invokeType` 路由到对应协议调用器
  - `resolve` 阶段统一归一化 `invokeType`（含 mcp 协议字段）
  - 不支持协议明确报错并阻断

## 3) 统一资源注册闭环补齐（本轮）

- 新增文件：
  - `src/main/java/com/lantu/connect/gateway/controller/ResourceRegistryController.java`
  - `src/main/java/com/lantu/connect/gateway/service/ResourceRegistryService.java`
  - `src/main/java/com/lantu/connect/gateway/service/impl/ResourceRegistryServiceImpl.java`
  - `src/main/java/com/lantu/connect/gateway/service/support/ResourceLifecycleStateMachine.java`
- 审核链路扩展：
  - `src/main/java/com/lantu/connect/audit/controller/AuditController.java`
  - `src/main/java/com/lantu/connect/audit/service/AuditService.java`
  - `src/main/java/com/lantu/connect/audit/service/impl/AuditServiceImpl.java`
- 能力覆盖：
  - 统一写路径（create/update/delete）
  - 提审/审核/发布/下线闭环
  - 版本创建与切换
  - MCP 协议可调用性校验（创建/更新时校验协议是否被网关支持）
- 数据库变更说明：
  - 本轮仅使用既有表：`t_resource`、`t_resource_*_ext`、`t_resource_version`、`t_audit_item`
  - **无新增或修改表结构**

## 验证
- 编译通过：`./mvnw.cmd -DskipTests compile`
- 无新增 linter 报错。
