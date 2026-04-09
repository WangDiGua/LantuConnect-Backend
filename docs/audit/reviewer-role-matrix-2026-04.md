# 审核员（reviewer）页面 × HTTP × 后端能力矩阵（2026-04）

> **2026-04-09**：`/grant-applications*`、`/resource-grants*` 与相关表已删除。下文若仍出现 **`grant-applications` / `resource-grant-management` / `GrantApplicationController`**，视为 **历史门禁与菜单口径**，前端/权限应逐步收敛；验收以 `PRODUCT_DEFINITION.md` §4 为准。

本文档与 Flyway [`V15__reviewer_permissions_align.sql`](../../sql/incremental/V15__reviewer_permissions_align.sql) 及前端侧栏/门禁实现同步，用于全量对照与环境验收。

**角色前提**：平台角色 `reviewer`；JWT 主角色可能与 Casbin 多权限并存；界面以 `/auth/me` 的 `permissions`（库表 `t_platform_role.permissions` 合并）为准。

## 1. 导航与门禁（前端）

| 侧栏/路由域 | 关键文件（与后端仓同级的 `LantuConnect-Frontend`） |
|-------------|----------|
| 路由与页面注册 | `LantuConnect-Frontend/src/constants/consoleRoutes.ts` |
| 分组与子项 | `LantuConnect-Frontend/src/constants/navigation.ts` |
| 侧栏 `hasPermission` / `user.permissions` / 发布壳 | `LantuConnect-Frontend/src/layouts/MainLayout.tsx` |
| 静态回落映射 | `LantuConnect-Frontend/src/context/UserRoleContext.tsx`（`ROLE_PERMISSIONS.reviewer`） |

**专项行为**：

- 「平台管理 → 用户与权限」整块：除 `user:manage` 外，若具备 `user:read`、`grant-application:review`、`resource-grant:manage` 或 `developer-application:review` 亦展示；子项再由 `SUB_ITEM_PERM_MAP` 过滤。
- 用户壳「我的发布」：`canAccessUserPublishingShell` = 原 `*:create` **或** `platformRole` 为 `reviewer` / `platform_admin`。
- 应用侧 `/user/developer-applications`：需 `developer-application:review`。

## 2. 管理域 `/admin/*` 页面清单

| page（normalize 后） | 主要组件/说明 | 典型后端依赖 |
|---------------------|---------------|--------------|
| dashboard | Overview | `GET /dashboard/admin-overview` 等，`monitor:view` |
| health-check | HealthCheckOverview | `GET /dashboard/health-summary`，`monitor:view` |
| usage-statistics | UsageStatsOverview | `GET /dashboard/usage-stats`，`monitor:view` |
| data-reports | DataReportsPage | `GET /dashboard/data-reports`，`monitor:view` |
| resource-catalog | ResourceCenterManagementPage | 目录/注册 `ResourceRegistryController`、服务层 reviewer 代管 |
| skill-external-market | SkillExternalMarketPage | `system:config`；无权重定向 |
| *-register / agent-detail | ResourceRegisterPage、AgentDetail | 同上注册中心 |
| agent-monitoring / agent-trace | Monitoring 模块 | `MonitoringController`，多数 `monitor:view`；部分写操作仅超管 |
| resource-audit / legacy *-audit | ResourceAuditList | `AuditController`，`RequireRole` 含 reviewer |
| provider-list / provider-create | ProviderManagementPage | `ProviderController` 仅 `platform_admin`/`admin`；子项门禁隐藏 reviewer |
| user-list | UserListPage | `GET /user-mgmt/users`，`user:read`（只读 UI 无 `user:update`） |
| role-management | RoleListPage | `role:manage` + 相关 API |
| organization | OrgStructurePage | `org:manage` + 相关 API |
| api-key-management | ApiKeyListPage | `api-key:manage`（Casbin 与后端 `apikey:*` 命名独立） |
| resource-grant-management | ResourceGrantManagementPage | `resource-grant:manage` 等 |
| grant-applications | GrantApplicationListPage | `GrantApplicationController` + 服务层全量待办 |
| developer-applications | DeveloperApplicationListPage | `DeveloperApplicationController`，`RequireRole` 含 reviewer |
| monitoring-* | MonitoringModule | 见 MonitoringController |
| system-config / tag-* | SystemConfigModule | 多数字段仅 `platform_admin` |

**仅超管（示例）**：`POST /audit/resources/{id}/platform-force-deprecate`；前端 `ResourceAuditList` 已限制按钮。

## 3. 应用域 `/user/*` 页面清单（与 reviewer 相关）

| page | 说明 |
|------|------|
| ~~grant-applications~~ | **历史**（接口已删；宜移除侧栏） |
| developer-applications | 入驻审批（2026-04 起纳入 `user-resource-assets` 导航） |
| resource-center 及五类 list/register | 受 `canAccessUserPublishingShell` 约束 |
| developer-portal（api-docs 等） | 需 service 侧 `developer:portal` |

## 4. 后端 Controller 摘要（reviewer）

| 区域 | Controller | reviewer |
|------|------------|----------|
| 审核 | `AuditController` | approve/reject/publish（除 platform-force-deprecate） |
| 入驻 | `DeveloperApplicationController` | 列表与审批 |
| ~~授权工单~~ | ~~`GrantApplicationController`~~ | **已删除** |
| 用户管理 | `UserMgmtController` | 无类级别角色门槛；具体方法由 `RequirePermission` / `RequireRole` 约束；列表只读需 `user:read` |
| 仪表盘 | `DashboardController` | `monitor:view` 类接口；`owner-resource-stats` 等服务层校验 |

## 5. 环境验收建议

1. 以 reviewer 账号登录，确认 `/auth/me` 的 `permissions` 含 V15 集合（顺序可不同）。
2. 侧栏：~~「授权审批待办」~~ 应对齐删除/静态说明；**「入驻审批」**仍可测；管理端「用户与权限」宜移除 ~~grant/resource-grant~~ 子项（若 JWT 仍带相关 permission，为遗留数据）。
3. 打开 `/user/resource-center` 与 `/admin/resource-catalog` 各做一次只读/代管操作，与后端 403 是否一致。

---

*本矩阵由「审核员前后端全量对齐」计划产出；后续新增页面请在本表追加行并更新 Flyway 或产品说明。*
