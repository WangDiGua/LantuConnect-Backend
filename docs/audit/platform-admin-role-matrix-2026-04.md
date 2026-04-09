# 超管（platform_admin）页面 × HTTP × 后端能力矩阵（2026-04）

> **2026-04-09**：**Grant / 授权申请** API 与表已删除。下列表格中 **`resource-grant-management` / `grant-applications`** 行视为历史；真值见 `PRODUCT_DEFINITION.md` §4。

本文档与 Flyway [`V16__platform_admin_permissions_menu_alignment.sql`](../../sql/incremental/V16__platform_admin_permissions_menu_alignment.sql) 及前端侧栏/门禁同步，用于全量对照与环境验收。

**角色前提**：平台角色 `platform_admin`（JWT 中历史别名 `admin` / `super_admin` 在前端 [`normalizeRole`](../../../LantuConnect-Frontend/src/context/UserRoleContext.tsx) 中归一为 `platform_admin`）。界面以 `/auth/me` 的 `permissions`（库表 `t_platform_role.permissions` 合并）为准；须同时满足后端 `RequireRole` / `RequirePermission`。

## 1. 导航与门禁（前端）

| 说明 | 路径（前端仓 `LantuConnect-Frontend`） |
|------|----------------------------------------|
| 路由注册 | `src/constants/consoleRoutes.ts` → `ADMIN_SIDEBAR_PAGES` / `USER_SIDEBAR_PAGES` |
| 分组子项 | `src/constants/navigation.ts` |
| 侧栏与 `SUB_ITEM_PERM_MAP` | `src/layouts/MainLayout.tsx` |
| 静态回落 | `src/context/UserRoleContext.tsx` → `ROLE_PERMISSIONS.platform_admin` |

**专项行为**：

- `canAccessAdminView`：`platform_admin` 与 `reviewer` 可进入 `/admin/*`。
- 「用户与权限」顶级项：除超管 / 审核员组合条件外，子项由 `SUB_ITEM_PERM_MAP` 过滤；V16 后库表含 **`role:manage` / `org:manage` / `api-key:manage`** 等与菜单一致的别名，并与既有 **`apikey:*` / `role:*` / `org:*`**  granular 共存。
- 用户壳「我的发布」：[`canAccessUserPublishingShell`](../../../LantuConnect-Frontend/src/layouts/MainLayout.tsx) 包含 `platformRole === 'platform_admin'`。
- `developer-onboarding`：对 `platform_admin` / `reviewer` 隐藏（产品约定）。

## 2. 管理域 `/admin/*` 页面清单（与 consoleRoutes 一致）

| page（normalize 后） | 组件/模块 | 典型后端/权限 |
|---------------------|-----------|----------------|
| dashboard | Overview | `GET /dashboard/admin-overview`，`monitor:view` |
| health-check | HealthCheckOverview | `GET /dashboard/health-summary`，`monitor:view` |
| usage-statistics | UsageStatsOverview | `GET /dashboard/usage-stats`，`monitor:view` |
| data-reports | DataReportsPage | `GET /dashboard/data-reports`，`monitor:view` |
| resource-catalog | ResourceCenterManagementPage | `ResourceRegistryController`；代管见服务层 |
| skill-external-market | SkillExternalMarketPage | `system:config` |
| *-register, agent-detail | ResourceRegisterPage, AgentDetail | 注册中心 |
| agent-monitoring, agent-trace | MonitoringModule | `MonitoringController`，读多为 `monitor:view` |
| resource-audit, *-audit | ResourceAuditList | `AuditController`；`platform-force-deprecate` 仅超管 |
| provider-list, provider-create | ProviderManagementPage | `ProviderController` `platform_admin`/`admin` |
| user-list | UserListPage | `UserMgmtController` `user:read` 等 |
| role-management | RoleListPage | `role:*` + 部分仅 `platform_admin` |
| organization | OrgStructurePage | `org:*` + 写入仅超管 |
| api-key-management | ApiKeyListPage | `apikey:*` |
| token-management | TokenListPage（UserManagementModule） | `apikey:read` / `apikey:delete` 等 |
| ~~resource-grant-management~~ | ~~ResourceGrantManagementPage~~ | **已下线** |
| ~~grant-applications~~ | ~~GrantApplicationListPage~~ | **已下线**；~~`GrantApplicationController`~~ **已删** |
| developer-applications | DeveloperApplicationListPage | `DeveloperApplicationController` |
| monitoring-overview…circuit-breaker | MonitoringModule | `MonitoringController` / `HealthController`；告警规则写操作为 `platform_admin` |
| tag-management…announcements | SystemConfigModule | `TagController`、`SystemParamController`、`SensitiveWordController`、`AnnouncementController` 等多为 `platform_admin` |
| network-config | SystemConfigModule（NetworkConfigPage） | 与「安全/访问」同壳；需 `system:config` 进系统配置分组 |

## 3. 应用域 `/user/*`（超管常用）

与 [`USER_SIDEBAR_PAGES`](../../../LantuConnect-Frontend/src/constants/consoleRoutes.ts) 一致：hub、workspace、resource-assets（含 market、~~grant~~、developer-applications）、developer-portal、settings。判权仍以 JWT + `permissions` + 各接口为准。

## 4. 仅代码存在、曾经无导航的项（已 products 补齐）

| 子项 id | 说明 | 现状（2026-04） |
|---------|------|-----------------|
| token-management | 访问令牌列表 / 吊销 | 已加入「用户与权限 → 凭证」与 `ADMIN_SIDEBAR_PAGES` |
| network-config | 网络配置页 | 已加入「系统配置 → 基础」与 `ADMIN_SIDEBAR_PAGES` |
| category-management | 旧路由别名 | `normalizeDeprecatedPage` → `tag-management` |

## 5. 语义说明：`audit:manage`

库表与前端静态表含 `audit:manage`；当前 Java **无** `@RequirePermission("audit:manage")` 用法，实际审核写接口多为 `AuditController` 的 `RequireRole`。矩阵中视为**产品/命名占位**；若将来统一为 permission-only，可再改注解。

## 6. 环境验收（回归）

1. `platform_admin` 登录，检查 `/auth/me` 的 `permissions` 含 V16 合并项（顺序无关）。
2. 侧栏：总览 / 资源与运营 / 用户与权限（含 **Token 管理**）/ 监控 / 系统配置（含 **网络配置**）均可展开且子链接与路由一致。
3. 冒烟：告警规则保存、系统参数保存、（可选）资源强制下架接口与 UI。

---

*后续新增 `page` 时请同步更新本表、`ADMIN_SIDEBAR_PAGES` 与 Flyway 权限（若引入新 Casbin 串）。*
