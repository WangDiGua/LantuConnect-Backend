# SDK OpenAPI 与沙箱后端落地

## 本次目标
- 输出稳定 SDK v1 网关接口（独立路径，便于后续版本治理）。
- 提供后端沙箱会话与隔离调用能力，支持开发者调试与限额控制。

## 已落地接口

### SDK 稳定接口（`/sdk/v1`）
- `GET /sdk/v1/resources`：目录查询
- `GET /sdk/v1/resources/{type}/{id}`：按类型+ID查询
- `POST /sdk/v1/resolve`：资源解析
- `POST /sdk/v1/invoke`：统一调用

实现位置：`src/main/java/com/lantu/connect/gateway/controller/SdkGatewayController.java`

说明：
- 与现有网关逻辑共用 `UnifiedGatewayService`，保证行为一致。
- 增加 OpenAPI 注解（`@Tag`、`@Operation`）用于文档稳定输出。

### 沙箱接口（`/sandbox`）
- `POST /sandbox/sessions`：创建沙箱会话（需 `X-User-Id` + `X-Api-Key`）
- `GET /sandbox/sessions/mine`：查询我的沙箱会话
- `POST /sandbox/invoke`：使用 `X-Sandbox-Token` 发起隔离调用

实现位置：
- `src/main/java/com/lantu/connect/sandbox/controller/SandboxController.java`
- `src/main/java/com/lantu/connect/sandbox/service/impl/SandboxServiceImpl.java`

## 沙箱隔离策略（当前版本）
- 会话生命周期：`active/expired`，到期自动失效。
- 调用限额：`max_calls` 与 `used_calls` 控制会话可用次数。
- 超时上限：会话级 `max_timeout_sec`，调用请求超时时间自动收敛。
- 资源类型白名单：会话级 `allowed_resource_types` 控制可调用资源类型。
- API Key 继承：沙箱调用复用会话绑定的 API Key 与 scope 鉴权。

## 数据库变更
- 基线表：`sql/lantu_connect.sql` 中包含 `t_sandbox_session`
- 增量脚本：统一维护于 `sql/migrations/`（按命名顺序执行）

主要字段：
- `session_token`：沙箱令牌
- `owner_user_id`：会话归属用户
- `api_key_id`：绑定 API Key
- `allowed_resource_types`：可调用资源类型（JSON）
- `max_calls`、`used_calls`：调用限额
- `max_timeout_sec`：超时上限
- `expires_at`：过期时间

## 验证结果
- SQL 执行成功，`t_sandbox_session` 已创建。
- 工程编译通过：`./mvnw.cmd -DskipTests compile`。
