# 后端契约冻结基线

## 冻结信息
- 冻结版本：`2026-03-contract-v1`
- 生效日期：`2026-03-24`
- 生效范围：后端与数据库（不含前端页面实现）

## 1. 接口契约冻结

### 1.1 统一网关（平台内部）
- `GET /catalog/resources`
- `GET /catalog/resources/{type}/{id}`
- `POST /catalog/resolve`
- `POST /invoke`

### 1.2 SDK 稳定接口（对外）
- `GET /sdk/v1/resources`
- `GET /sdk/v1/resources/{type}/{id}`
- `POST /sdk/v1/resolve`
- `POST /sdk/v1/invoke`

### 1.3 沙箱接口
- `POST /sandbox/sessions`
- `GET /sandbox/sessions/mine`
- `POST /sandbox/invoke`

## 2. 鉴权契约冻结
- 人员鉴权：`X-User-Id` + 角色权限（RBAC）生效。
- 应用鉴权：`X-Api-Key` + scope 生效。
- SDK v1：`X-Api-Key` 必传。
- 统一规则：资源访问需同时满足“用户角色权限”和“应用 scope”。

## 3. 资源与治理状态机冻结

### 3.1 资源状态（目录主状态）
- `draft`：草稿
- `testing`：测试中
- `published`：已发布
- `deprecated`：已废弃（可读，默认不建议新接入）

允许流转：
- `draft -> testing`
- `testing -> published`
- `published -> deprecated`
- `testing -> draft`（回退修订）

不允许直接流转：
- `draft -> published`
- `deprecated -> published`

### 3.2 健康状态
- `healthy`
- `degraded`
- `disabled`

### 3.3 熔断状态
- `CLOSED`
- `OPEN`
- `HALF_OPEN`

## 4. 数据模型冻结
- 核心资源模型：`t_resource` + `t_resource_*_ext`
- 资源关系：`t_resource_relation`
- 治理模型：
  - `t_resource_health_config`
  - `t_resource_circuit_breaker`
- 沙箱模型：`t_sandbox_session`

## 5. 切换与回滚冻结
- 当前为最终态：统一网关固定读取 `t_resource*` 新模型。
- 回滚原则：通过数据库备份与发布回退，不再依赖旧模型切换开关。

## 6. 变更纪律（冻结后）
- 不允许无版本号地修改 `/sdk/v1/**` 返回结构。
- 任何状态机新增状态必须先补迁移脚本与兼容说明，再进入版本化发布（如 `v2`）。
- 任何鉴权语义变化必须同步更新：
  - `docs/backend/api/api-scope-rulebook.md`
  - `docs/backend/api/dual-authz-enforcement.md`
