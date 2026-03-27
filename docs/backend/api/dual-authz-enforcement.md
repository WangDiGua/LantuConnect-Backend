# 双层鉴权统一生效说明

## 目标
- 人员侧走角色权限（RBAC）。
- 应用侧走 API Key scope。
- 统一网关在同一调用链上同时支持两层鉴权，并对同一资源类型做一致判定。

## 本次落地

### 1) 新增网关用户权限服务
- 文件：`src/main/java/com/lantu/connect/gateway/security/GatewayUserPermissionService.java`
- 能力：
  - 当存在 `X-User-Id` 时，按用户角色权限判断是否可访问资源类型。
  - `platform_admin` 放行全部类型。
  - 类型映射：
    - `agent` -> `agent:read` 或 `skill:read`
    - `skill` / `mcp` -> `skill:read`
    - `app` -> `app:view`
    - `dataset` -> `dataset:read`

### 2) 统一网关服务接入用户鉴权
- 文件：`src/main/java/com/lantu/connect/gateway/service/impl/UnifiedGatewayServiceImpl.java`
- 变更：
  - `catalog`：按用户权限过滤可见资源类型。
  - `resolve/getByTypeAndId`：先做用户资源类型权限校验，再做 API Key scope 校验。
  - `invoke`：通过 `getByTypeAndId` 复用双层校验链。

### 3) 接口签名升级（带 userId 上下文）
- 文件：`src/main/java/com/lantu/connect/gateway/service/UnifiedGatewayService.java`
- 变更：
  - `catalog/resolve/getByTypeAndId` 新增 `Long userId` 参数。

### 4) 控制器统一传递 userId
- 文件：
  - `src/main/java/com/lantu/connect/gateway/controller/ResourceCatalogController.java`
  - `src/main/java/com/lantu/connect/gateway/controller/SdkGatewayController.java`
- 说明：
  - 透传 `X-User-Id` 给统一网关服务，触发 RBAC 判定。
  - `SDK v1` 接口中的 `X-Api-Key` 调整为必传，确保应用 scope 始终生效。

## 验证
- 编译通过：`./mvnw.cmd -DskipTests compile`
- 本轮改动无新增 linter 报错。
