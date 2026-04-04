# 07 Controller 覆盖矩阵（后端全量）

> 用于审计“是否覆盖整个后端项目功能”。


| Controller                       | 路由前缀                           | 对应文档           |
| -------------------------------- | ------------------------------ | -------------- |
| `AuthController`                 | `/auth`                        | `02`、`05`      |
| `CaptchaController`              | `/captcha`                     | `02`           |
| `DashboardController`            | `/dashboard`                   | `02`、`01`      |
| `ResourceCatalogController`      | `/catalog`、`/invoke`           | `02`、`03`、`04` |
| `ResourceRegistryController`     | `/resource-center/resources`   | `02`、`03`、`04` |
| `AuditController`                | `/audit`                       | `02`、`03`      |
| `ResourceGrantController`        | `/resource-grants`             | `02`、`03`      |
| `SdkGatewayController`           | `/sdk/v1`                      | `02`           |
| `SandboxController`              | `/sandbox`                     | `02`           |
| `DeveloperApplicationController` | `/developer/applications`      | `02`、`06`      |
| `DeveloperStatisticsController`  | `/developer/my-statistics`     | `01`、`02`      |
| `UserSettingsController`         | `/user-settings`               | `02`、`04`      |
| `UserActivityController`         | `/user`                        | `02`、`04`      |
| `UserMgmtController`             | `/user-mgmt`                   | `02`、`04`      |
| `MonitoringController`           | `/monitoring`                  | `02`、`04`      |
| `HealthController`               | `/health`                      | `02`、`04`      |
| `SystemParamController`          | `/system-config`               | `02`           |
| `RateLimitRuleController`        | `/system-config/rate-limits`   | `02`           |
| `QuotaController`                | `/quotas`                      | `02`           |
| `QuotaRateLimitController`       | `/rate-limits`                 | `02`           |
| `AnnouncementController`         | `/system-config/announcements` | `02`           |
| `TagController`                  | `/tags`                        | `02`           |
| `SensitiveWordController`        | `/sensitive-words`             | `02`           |
| `NotificationController`         | `/notifications`               | `02`、`05`      |
| `ReviewController`               | `/reviews`                     | `02`、`04`      |
| `FileController`                 | `/files`                       | `02`           |


## 审计规则

- 新增 Controller：必须同步更新 `02` + 本文档。
- Controller 在此存在但页面未接：视为“后端已实现、前端待对齐”。

## 完整性检查清单

- [x] Controller 条目总数与代码一致（27）
- [x] 每个 Controller 至少映射到一个执行文档
- [x] 核心链路 Controller（Catalog/Registry/Audit/Grant/Auth）均已覆盖

