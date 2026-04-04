# Developer（开发者）角色前端页面与后端全量对齐审查 — 2026-04

**节奏**：Superpowers（`using-superpowers`、`verification-before-completion`）+ 项目 [lantuconnect-backend-audit](../../.cursor/skills/lantuconnect-backend-audit/SKILL.md)。

**范围**：前端 `LantuConnect-Frontend` 中「开发者」相关的三类叙事（见 §0）、路由 **`/#/user/:page`** 与 **`/#/admin/developer-applications`**、`USER_SIDEBAR_PAGES` 与 `ADMIN_SIDEBAR_PAGES` 登记项，以及 `UserRoleContext.ROLE_PERMISSIONS.developer` 门禁。HTTP 相对 `VITE_API_BASE_URL`（常与手册一致叠加 `/api` 与 `context-path`）。

---

## 0. 范围分层（避免漏项）

| 层级 | 含义 | 路由/入口 |
|------|------|-----------|
| **A. 平台角色 `developer`** | `UserRoleContext` 中 `ROLE_PERMISSIONS.developer`（含 `developer:portal`、五类资源 create/publish、`grant-application:review`） | `/user/developer-portal` 子页、`/user/resource-center*`、注册页、`UserWorkspaceOverview` 中带 `perm: 'developer:portal'` 的快捷入口 |
| **B. 入驻申请人（常为非 developer）** | 申请成为开发者 | `/user/developer-onboarding`（`DeveloperOnboardingPage` embedded）；**侧栏子项对 `platformRole` 为 `developer` / `platform_admin` / `reviewer` 隐藏**（`MainLayout#filteredSubGroupsForSidebarId`） |
| **C. 审核侧（reviewer / platform_admin）** | 审批入驻 | `/admin/developer-applications` → `DeveloperApplicationListPage` |

**资源浏览/发布依赖**：developer 与消费者共用 `user-resource-assets` 下市场与「我的发布」；无 `*:create` 之一则 `requiresPublish` 分组整组隐藏。全量 slug 与 HTTP 主链路与 User 审查文档 **交叉引用**：[frontend-user-role-alignment-2026-04.md](frontend-user-role-alignment-2026-04.md) §1–§2（不重复展开五类市场）。

**等价入口**：前端 `src/constants/spaces.ts` 中 `user-developer` 与侧栏 id `developer-portal` 均挂载 `ADMIN_DEVELOPER_PORTAL_GROUPS`（`src/constants/navigation.ts`：api-docs / sdk-download / api-playground / developer-statistics）。

---

## 1. 全量页面清单（developer 主线）

### 1.1 `/#/user/...`（`consoleRole === 'user'`）

| page slug | 侧栏/分组 | 组件 | Placeholder? | 门禁与备注 |
|-----------|-----------|------|--------------|------------|
| `api-docs` | 开发者中心 | `ApiDocsPage` | 否 | 须 `hasPermission('developer:portal')`；直连且缺权时 `MainLayout` 重定向 `defaultPath()`（`DEVELOPER_PORTAL_PAGES`） |
| `sdk-download` | 开发者中心 | `SdkDownloadPage` | 否 | 同上 |
| `api-playground` | 开发者中心 | `ApiPlaygroundPage` | 否 | 同上 |
| `developer-statistics` | 开发者中心 | `DeveloperStatsPage` | 否 | 同上；拉取 `GET /developer/my-statistics` 与 `GET /dashboard/owner-resource-stats` |
| `developer-onboarding` | 我的工作台 | `DeveloperOnboardingPage` | 否 | 侧栏对 `developer`/`platform_admin`/`reviewer` 隐藏子项；路由仍可书签直达 |
| 发布与市场 | 资源与资产 | `ResourceCenterManagementPage`、`*Market`、`MyPublishHubPage` … | 否 | 须 `canPublishResources`；否则从资源登记路由踢回 `hub` |

### 1.2 `/#/admin/developer-applications`

| page slug | 侧栏 | 组件 | Placeholder? | 门禁 |
|-----------|------|------|--------------|------|
| `developer-applications` | 用户与权限 | `DeveloperApplicationListPage` | 否 | `SUB_ITEM_PERM_MAP` → `developer-application:review`；且须 `canAccessAdminView(platformRole)` |

---

## 2. 页面 → HTTP → 后端

| 页面/场景 | 前端服务 | HTTP | 后端 |
|-----------|----------|------|------|
| 入驻申请/我的申请 | `developerApplicationService` | `POST /developer/applications`、`GET /developer/applications/me` | [`DeveloperApplicationController`](../../src/main/java/com/lantu/connect/onboarding/controller/DeveloperApplicationController.java) |
| 入驻审批列表/通过/驳回 | 同上 | `GET /developer/applications`、`POST .../approve`、`POST .../reject` | 同上；列表与审批方法带 `@RequireRole({"platform_admin","admin","reviewer"})` |
| 开发者统计 · 个人概览 | `developerStatsService` | `GET /developer/my-statistics` | [`DeveloperStatisticsController`](../../src/main/java/com/lantu/connect/onboarding/controller/DeveloperStatisticsController.java) + [`DeveloperStatisticsServiceImpl`](../../src/main/java/com/lantu/connect/onboarding/service/impl/DeveloperStatisticsServiceImpl.java)（`t_call_log` 按 `user_id`） |
| 开发者统计 · Owner 成效 | `developerStatsService` | `GET /dashboard/owner-resource-stats` | [`DashboardController`](../../src/main/java/com/lantu/connect/dashboard/controller/DashboardController.java) + `OwnerDeveloperStatsService` |
| 接入指南/SDK 页 | 静态为主 | 文档中列举的路径 | 与 [frontend-alignment-handbook.md](../frontend-alignment-handbook.md) 网关/SDK 章节一致 |
| Playground | `http` | `/catalog/*`、`/invoke` 等 | 统一网关控制器 |

---

## 3. RBAC 对账

| 维度 | 前端 | 后端 | 结论 |
|------|------|------|------|
| 开发者中心侧栏 | `developer:portal`（`ROLE_PERMISSIONS.developer` 含此权限） | 路由无单独 `@RequirePermission`；网关 JWT 与用户身份决定 | 一致：缺 `developer:portal` 时前端隐藏 + 直达拦截 |
| 入驻审批 | `developer-application:review` | `@RequireRole({"platform_admin","admin","reviewer"})` | 一致：前端 reviewer/platform_admin 合成权限含 `developer-application:review`；**`admin` 角色码**须与 JWT/库表一致（与 `platform_admin` 并存时为兼容别称，以真值为 [`lantuconnect-backend-audit`](../../.cursor/skills/lantuconnect-backend-audit/SKILL.md) 所述 `t_platform_role`） |
| 资源发布 | `canPublishResources`（create 权限聚合） | `ResourceRegistry` / Casbin | 与 User 审查 §4 同逻辑 |

---

## 4. 契约与已实现修复

| 优先级 | 项 | 说明 |
|--------|-----|------|
| **P0** | `GET /developer/applications/me` 返回 **列表** | 前端 `getMine` 已归一：`Array.isArray` 时 **优先 `pending`**，否则取列表首条（与后端 `create_time DESC` 一致）；`toApplication` 补充 **snake_case** 字段映射 |
| **P1** | `GET /developer/my-statistics` 无页面消费 | `DeveloperStatsPage` 已并行拉取并展示 **个人概览** KPI + Top 资源 / API Key，与 Owner 成效 **分区说明口径差异** |
| **P2** | 手册历史漂移 | [frontend-alignment-handbook.md](../frontend-alignment-handbook.md) 入驻列表/审批已由「`RequirePermission(user:manage)`」更正为 **`@RequireRole`**；§5.5 补充列表鉴权说明 |

---

## 5. 产品闭环（简评）

- **入驻**：申请 → 通知（见 [notification-event-matrix.md](../notification-event-matrix.md) `onboarding_*`）→ 通过赋权 → 发布管线。
- **可发现性**：已开发者隐藏「入驻」符合预期；必要时可在 `ApiDocsPage` 增加短文说明「发布权限」链接（P2）。
- **双统计**：页内已区分「个人 call_log」与「Owner 资源成效」，避免误读总数。

---

## 6. 验证（verification-before-completion）

| 检查项 | 结果 |
|--------|------|
| developer 相关 user slug 均有 `MainLayout` `case` 或文档标明 admin 专用 | **通过** |
| 主干入驻/统计 API 与 Controller 映射 | **通过** |
| `mvn test` | **已通过**（2026-04-03） |
| `npm run build`（前端） | **已通过**（2026-04-03） |

---

## 7. 维护建议

- 新增开发者中心子页时：同步前端 `src/constants/consoleRoutes.ts` 中 `USER_SIDEBAR_PAGES['developer-portal']`、`src/constants/navigation.ts` 的 `ADMIN_DEVELOPER_PORTAL_GROUPS`、`MainLayout.tsx` 的 `case` 与 `DEVELOPER_PORTAL_PAGES`。
- 双仓对齐链接：本仓库 [frontend-alignment-handbook.md](../frontend-alignment-handbook.md) §1。
