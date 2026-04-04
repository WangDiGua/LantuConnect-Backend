# 后端 Controller 清单（与代码一致）

**说明**：以下路径为 **Servlet 路径**（相对 `server.servlet.context-path`，本地示例需加前缀 `/regis`）。  
**`@RestController` 数量**：**30**（不含 `GlobalExceptionHandler`）。  
**最后与同仓代码对账**：2026-04-04（移除 `ModelConfigController`；补充 `SkillExternalCatalog*` 两行）。

| # | Controller | `RequestMapping` 前缀 | 类级 / 典型鉴权 |
|---|------------|------------------------|-----------------|
| 1 | `AuthController` | `/auth` | 白名单（见 SecurityConfig）+ 业务登录 |
| 2 | `CaptchaController` | `/captcha` | 白名单 |
| 3 | `FileController` | `/files` | 按方法（认证用户） |
| 4 | `HealthController` | `/health` | `monitor:view` / `platform_admin` 分方法 |
| 5 | `MonitoringController` | `/monitoring` | `monitor:view` + 部分 `platform_admin` |
| 6 | `DashboardController` | `/dashboard` | `monitor:view`（多数） |
| 7 | `ResourceRegistryController` | `/resource-center/resources` | 资源 owner / 角色组合（见类内） |
| 8 | `ResourceGrantController` | `/resource-grants` | 同上 |
| 9 | `GrantApplicationController` | `/grant-applications` | 工单逻辑 + 服务层校验 |
| 10 | `SdkGatewayController` | `/sdk/v1` | SDK 路由 |
| 11 | `SandboxController` | `/sandbox` | `platform_admin` / `admin` / `reviewer` / `developer`（会话） |
| 12 | `AuditController` | `/audit` | `platform_admin` / `reviewer`；发布含 `developer`；强制下架仅 `platform_admin` |
| 13 | `UserMgmtController` | `/user-mgmt` | 类级 `platform_admin`+`admin`；方法级 `RequirePermission`（部分仅 `platform_admin`） |
| 14 | `DeveloperApplicationController` | `/developer/applications` | 列表/审批 `platform_admin`+`admin`+`reviewer` |
| 15 | `DeveloperStatisticsController` | `/developer` | `GET /my-statistics` 等 |
| 16 | `UserSettingsController` | `/user-settings` | 登录用户 |
| 17 | `UserActivityController` | `/user` | 登录用户 |
| 18 | `NotificationController` | `/notifications` | 登录用户 |
| 19 | `ReviewController` | `/reviews` | 按方法 |
| 20 | `SystemParamController` | `/system-config` | `platform_admin` |
| 21 | `RateLimitRuleController` | `/system-config/rate-limits` | `platform_admin` |
| 22 | `AnnouncementController` | `/system-config/announcements` | `platform_admin` |
| 23 | `QuotaController` | `/quotas` | `platform_admin` |
| 24 | `QuotaRateLimitController` | `/rate-limits` | `platform_admin` |
| 25 | `TagController` | `/tags` | `platform_admin` |
| 26 | `ProviderController` | `/providers`，`/dataset/providers` | `platform_admin`+`admin` |
| 27 | `SensitiveWordController` | `/sensitive-words` | `platform_admin` |
| 28 | `SkillExternalCatalogController` | `/resource-center/skill-external-catalog` | `system:config`（GET 列表） |
| 29 | `SkillExternalCatalogSettingsController` | `/resource-center/skill-external-catalog` | `system:config`（`/settings` GET/PUT） |
| 30 | `ResourceCatalogController` | **无类级前缀** | 见下表 |

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

## 角色模型（与 2026-04 后端一致）

- 平台角色：`user`、`developer`、`reviewer`、`platform_admin`（JWT 中 `admin` 映射为超管能力需与同仓 `AuthServiceImpl` 一致）。
- **reviewer** 可走审核、Grant/入驻工单、沙箱等；**不**具备 `UserMgmtController` 类级入口（仅 `platform_admin`+`admin`）。
- 详见仓库 [`.cursor/skills/lantuconnect-backend-audit/SKILL.md`](../../.cursor/skills/lantuconnect-backend-audit/SKILL.md)。
