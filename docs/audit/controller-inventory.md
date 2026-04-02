# 后端 Controller 清单（与代码一致）

**说明**：以下路径为 **Servlet 路径**（相对 `server.servlet.context-path`，默认需加前缀 `/regis`）。  
**REST Controller 数量**：29（不含 `GlobalExceptionHandler`）。

| # | Controller | `RequestMapping` 前缀 |
|---|------------|------------------------|
| 1 | `AuthController` | `/auth` |
| 2 | `CaptchaController` | `/captcha` |
| 3 | `FileController` | `/files` |
| 4 | `HealthController` | `/health` |
| 5 | `MonitoringController` | `/monitoring` |
| 6 | `DashboardController` | `/dashboard` |
| 7 | `ResourceRegistryController` | `/resource-center/resources` |
| 8 | `ResourceGrantController` | `/resource-grants` |
| 9 | `GrantApplicationController` | `/grant-applications` |
| 10 | `SdkGatewayController` | `/sdk/v1` |
| 11 | `SandboxController` | `/sandbox` |
| 12 | `AuditController` | `/audit` |
| 13 | `UserMgmtController` | `/user-mgmt` |
| 14 | `DeveloperApplicationController` | `/developer/applications` |
| 15 | `DeveloperStatisticsController` | `/developer`（方法：`/my-statistics`） |
| 16 | `UserSettingsController` | `/user-settings` |
| 17 | `UserActivityController` | `/user` |
| 18 | `NotificationController` | `/notifications` |
| 19 | `ReviewController` | `/reviews` |
| 20 | `SystemParamController` | `/system-config` |
| 21 | `ModelConfigController` | `/system-config/model-configs` |
| 22 | `RateLimitRuleController` | `/system-config/rate-limits` |
| 23 | `AnnouncementController` | `/system-config/announcements` |
| 24 | `QuotaController` | `/quotas` |
| 25 | `QuotaRateLimitController` | `/rate-limits` |
| 26 | `TagController` | `/tags` |
| 27 | `ProviderController` | `/providers`，`/dataset/providers` |
| 28 | `SensitiveWordController` | `/sensitive-words` |
| 29 | `ResourceCatalogController` | **无类级前缀**（见下表） |

## ResourceCatalogController（方法级路径）

| 方法 | 路径 |
|------|------|
| GET | `/catalog/resources` |
| GET | `/catalog/resources/trending` |
| GET | `/catalog/resources/search-suggestions` |
| GET | `/catalog/resources/{type}/{id}` |
| GET | `/catalog/resources/{type}/{id}/stats` |
| POST | `/catalog/resolve` |
| POST | `/invoke` |
| POST | `/invoke-stream` |
| GET | `/catalog/apps/launch` |
