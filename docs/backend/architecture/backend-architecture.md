# NexusAI Connect 后端架构基线

## 1. 平台定位

本次重构后，后端同时承担四种职责：

- 注册平台（资源目录与生命周期）
- 调度平台（统一调用网关）
- 治理平台（健康检查、质量度量、熔断降级）
- 开发者平台（SDK 契约与沙箱后端能力）

本阶段仅覆盖后端与数据库，不包含前端实现。

## 2. 身份与权限模型

### 2.1 权威数据来源

- 用户与组织主数据：与公司主平台保持一致（同步为准）。
- 权限控制：由本系统维护并生效。

### 2.2 人员角色

- `platform_admin`
- `dept_admin`
- `developer`

无任何角色绑定的账号定义为 `unassigned`：

- 可登录
- 仅可提交开发者入驻申请
- 不可访问管理接口

### 2.3 集成调用方模型

老板平台或第三方厂商不作为“人类角色”处理，统一使用：

- 应用身份（API Key）
- Scope（资源范围）控制

## 3. 访问语义

“发布权限”和“调用权限”必须分离：

- 能上架（`create/update/publish`）不代表能被任何调用方执行。
- 调用必须通过 `resourceType + resourceId + version + scope` 校验。

## 4. 后端核心契约

- `GET /catalog/resources`
- `GET /catalog/resources/{type}/{id}`
- `POST /catalog/resolve`
- `POST /invoke`

调用请求至少包含：

- 资源标识（`resourceType`、`resourceId`、可选 `version`）
- 调用方身份（凭证）
- Scope 约束

## 5. 运行治理策略

### 5.1 按资源类型健康检查

- Agent/Skill/MCP：主动探测 + 调用反馈
- H5 应用：入口可达 + 关键依赖可达

### 5.2 熔断与降级

统一网关层承担超时、重试、熔断与统一错误返回。

### 5.3 可观测性

所有调用链路可追踪：

- requestId
- 目标资源
- 状态码/状态
- 延迟
- 调用方

## 6. 旧接口清理策略

旧接口按两阶段下线：

1. 先标记废弃并观察流量
2. 再执行物理删除（先禁写/隐藏，再删代码）

物理删除必须在清理文档审核后执行。

## 7. 契约冻结基线

本轮改造后的冻结基线见：

- `docs/backend/architecture/backend-contract-freeze.md`

## 8. 最终目录结构（收口后）

本轮目录收口后的后端目录说明见：

- `docs/backend/architecture/backend-directory-layout-final.md`

后端文档总览入口见：

- `docs/backend/README.md`
