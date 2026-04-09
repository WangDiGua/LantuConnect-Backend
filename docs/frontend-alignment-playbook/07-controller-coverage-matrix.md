# 07 Controller 覆盖矩阵（后端全量）

> 用于审计“是否覆盖整个后端项目功能”。**与代码同步日期：2026-04-09** — 明细路径见 [docs/audit/controller-inventory.md](../audit/controller-inventory.md)；**`ResourceGrantController` / `GrantApplicationController` 已物理删除**。

## REST Controller 表（**在役数量与枚举**以 [controller-inventory.md](../audit/controller-inventory.md) 为准；下表含 ~~Grant 系 2 行~~ 删除备忘，**非**完整枚举）

| Controller | 路由前缀 | 对应文档 |
| ---------- | -------- | -------- |
| `AuthController` | `/auth` | `02`、`05` |
| `CaptchaController` | `/captcha` | `02` |
| `FileController` | `/files` | `02` |
| `HealthController` | `/health` | `02`、`04` |
| `MonitoringController` | `/monitoring` | `02`、`04` |
| `DashboardController` | `/dashboard` | `02`、`01` |
| `ResourceCatalogController` | 无类级前缀：`/catalog/*`、`/invoke`、`/invoke-stream`、`/catalog/apps/launch` | `02`、`03`、`04` |
| `ResourceRegistryController` | `/resource-center/resources` | `02`、`03`、`04` |
| ~~`ResourceGrantController`~~ | ~~`/resource-grants`~~ | **已删除**（2026-04-09） |
| ~~`GrantApplicationController`~~ | ~~`/grant-applications`~~ | **已删除**（2026-04-09） |
| `SdkGatewayController` | `/sdk/v1` | `02` |
| `SandboxController` | `/sandbox` | `02` |
| `AuditController` | `/audit` | `02`、`03` |
| `UserMgmtController` | `/user-mgmt` | `02`、`04` |
| `DeveloperApplicationController` | `/developer/applications` | `02`、`06` |
| `DeveloperStatisticsController` | `/developer`（`GET /my-statistics`） | `01`、`02` |
| `UserSettingsController` | `/user-settings` | `02`、`04` |
| `UserActivityController` | `/user` | `02`、`04` |
| `NotificationController` | `/notifications` | `02`、`05` |
| `ReviewController` | `/reviews` | `02`、`04` |
| `SystemParamController` | `/system-config` | `02` |
| `RateLimitRuleController` | `/system-config/rate-limits` | `02` |
| `AnnouncementController` | `/system-config/announcements` | `02` |
| `QuotaController` | `/quotas` | `02` |
| `QuotaRateLimitController` | `/rate-limits` | `02` |
| `TagController` | `/tags` | `02` |
| `ProviderController` | `/providers`、`/dataset/providers` | `02` |
| `SensitiveWordController` | `/sensitive-words` | `02` |

## 审计规则

- 新增 Controller：必须同步更新 `02` + 本文档 + [docs/audit/controller-inventory.md](../audit/controller-inventory.md)。
- Controller 在此存在但页面未接：视为“后端已实现、前端待对齐”，并写入 [docs/audit/implementation-backlog.md](../audit/implementation-backlog.md)。

## 完整性检查清单

- [x] Controller 条目总数与 [controller-inventory.md](../audit/controller-inventory.md) 一致（**28** `@RestController`；`ResourceCatalogController` 为方法级映射另见 inventory）
- [x] 每个 Controller 至少映射到一个执行文档或可追踪审计行
- [x] 核心链路（Catalog / Registry / Auth / 入驻审批）均已覆盖；~~Grant~~ 已删
