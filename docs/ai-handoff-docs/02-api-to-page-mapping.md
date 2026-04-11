# 02 接口到页面映射总表（全量主链路）

> 目标：前端按页面查接口，且能看到权限、查询参数、返回字段最小集。

| 能力域 | 页面/功能 | 接口（Method + Path） | 权限/调用条件 | 常用查询参数 | 返回字段最小集 |
|---|---|---|---|---|---|
| 登录与鉴权 | `LoginPage/RegisterPage`、应用启动恢复 | `GET /captcha/generate`、`POST /captcha/verify`、`POST /auth/login`、`POST /auth/register`、`POST /auth/refresh`、`GET /auth/me`、`POST /auth/logout` | 匿名白名单 + 登录态 | 登录：`username/password/captchaId/captchaCode`；注册：`username/password/phone?/smsCode?` | `token/refreshToken/userInfo`、`captchaId/image` |
| 资料与安全 | `profile/preferences` | `PUT /auth/profile`、`POST /auth/change-password`、`POST /auth/send-sms`、`POST /auth/bind-phone`、`GET /auth/login-history`、`GET /auth/sessions`、`DELETE /auth/sessions/{sessionId}`、`GET/PUT /user-settings/workspace`、`GET/POST/DELETE /user-settings/api-keys*`、`GET /user-settings/stats` | 登录态用户 | 登录历史/会话：`page/pageSize` | 资料字段、会话字段、设置字段 |
| 探索与看板 | `hub/workspace/dashboard/*` | `GET /dashboard/explore-hub`、`GET /dashboard/user-workspace`、`GET /dashboard/admin-overview`、`GET /dashboard/health-summary`、`GET /dashboard/usage-stats`、`GET /dashboard/data-reports`、`GET /dashboard/admin-realtime`、`GET /dashboard/user-dashboard` | 角色 + 权限 | `range/startDate/endDate` | KPI、趋势、列表、健康摘要 |
| 目录检索与详情 | 五市场/详情 | `GET /catalog/resources`、`GET /catalog/resources/trending`、`GET /catalog/resources/search-suggestions`、`GET /catalog/resources/{type}/{id}`、`GET /catalog/resources/{type}/{id}/stats` | 登录态或 API Key（看 scope） | `resourceType/status/keyword/page/pageSize/sortBy/sortOrder/categoryId/tags[]` | `list[].resourceId/displayName/status`、`total`、详情字段 |
| 资源解析 | 详情使用前 | `POST /catalog/resolve` | 登录态或 API Key + scope | `resourceType/resourceId/version?` | `invokeType/endpoint/spec/status` |
| 标准调用 | 立即使用/调试 | `POST /invoke` | API Key + scope + grant | body: `resourceType/resourceId/version?/timeoutSec?/payload` | `traceId/statusCode/latencyMs/body` |
| SDK 通道 | SDK 页面/Playground | `GET /sdk/v1/resources`、`GET /sdk/v1/resources/{type}/{id}`、`POST /sdk/v1/resolve`、`POST /sdk/v1/invoke` | API Key + scope + grant | 同目录检索 | 同解析/调用 |
| 沙箱通道 | 沙箱调试页 | `POST /sandbox/sessions`、`GET /sandbox/sessions/mine`、`POST /sandbox/invoke` | 登录态 + `X-Sandbox-Token` | 会话：`page/pageSize/status?` | `sessionId/token/expiredAt`、调用结果 |
| 统一资源中心 | `*-register/*-list/resource-center` | `GET /resource-center/resources/mine`、`GET /resource-center/resources/{id}`、`POST /resource-center/resources`、`PUT /resource-center/resources/{id}`、`DELETE /resource-center/resources/{id}`、`POST /resource-center/resources/{id}/submit`、`POST /resource-center/resources/{id}/withdraw`、`POST /resource-center/resources/{id}/deprecate`、`GET /resource-center/resources/{id}/versions`、`POST /resource-center/resources/{id}/versions`、`POST /resource-center/resources/{id}/versions/{version}/switch` | `developer/dept_admin/platform_admin`（按资源归属与策略） | `resourceType/status/keyword/page/pageSize` | `id/resourceCode/displayName/status/version/updateTime` |
| 审核中心 | `audit-center/*` | `GET /audit/resources`、`POST /audit/resources/{id}/approve`、`POST /audit/resources/{id}/reject`、`POST /audit/resources/{id}/publish`、`GET /audit/agents`、`GET /audit/skills`、`POST /audit/agents/{id}/approve|reject|publish`、`POST /audit/skills/{id}/approve|reject|publish` | 审核角色/权限点 | `resourceType/status/keyword/page/pageSize` | `id/resourceType/displayName/status/submitter/submitTime` |
| ~~授权中心~~ | ~~`resource-grant-management`~~ | ~~`GET /resource-grants`、`POST /resource-grants`、`DELETE /resource-grants/{grantId}`~~ | ~~已废弃（2026-04-09 下线）~~；替代方案：API Key + Scope + `published` 状态 |
| 入驻流程 | `DeveloperOnboardingPage/developer-applications` | `POST /developer/applications`、`GET /developer/applications/me`、`GET /developer/applications`、`POST /developer/applications/{id}/approve`、`POST /developer/applications/{id}/reject`、`GET /developer/my-statistics` | 无角色用户申请；管理员审批 | `status/page/pageSize` | 申请单状态字段、统计字段 |
| 用户活动 | `my-favorites/recent-use/my-*` | `GET /user/usage-records`、`GET /user/favorites`、`POST /user/favorites`、`DELETE /user/favorites/{id}`、`GET /user/usage-stats`、`GET /user/my-agents`、`GET /user/my-skills`、`GET /user/authorized-skills`、`GET /user/recent-use` | 登录态用户 | `type/dateRange/page/pageSize` | 收藏、最近使用、统计字段 |
| 用户与组织 | `user-list/role-management/organization/api-key-management` | `/user-mgmt/users*`、`/user-mgmt/roles*`、`/user-mgmt/api-keys*`、`/user-mgmt/org-tree`、`/user-mgmt/orgs*`、`/user-mgmt/users/{id}/org*`、`/user-mgmt/users/{id}/roles*` | `user:manage` 等权限点 | `keyword/status/page/pageSize/orgId/roleId` | 用户/角色/组织/API Key 字段 |
| 监控与健康 | `monitoring/*`、`health-config`、`circuit-breaker` | `/monitoring/kpis`、`/monitoring/performance`、`/monitoring/call-logs`、`/monitoring/alerts`、`/monitoring/traces`、`/monitoring/alert-rules*`、`/monitoring/alert-rules/{id}/dry-run`、`/health/configs*`、`/health/circuit-breakers*`、`/health/security-config` | `monitor:view` / `system:config` | `keyword/status/level/timeRange/page/pageSize` | KPI、日志、告警、trace、熔断状态 |
| 系统治理 | `system-config/*`、配额限流、标签公告、文件上传 | `/system-config/params|security|audit-logs|network/apply|acl/publish`、`/system-config/rate-limits*`、`/system-config/announcements*`、`/quotas*`、`/rate-limits*`、`/tags*`、`/sensitive-words*`、`POST /files/upload`（`/system-config/model-configs*` **已移除**） | `system:config` 或业务权限 | `keyword/category/status/page/pageSize` | 配置项、配额、限流、标签、敏感词、公告、上传结果 |
| 消息与评价 | 顶栏消息、市场评价 | `GET /notifications`、`GET /notifications/unread-count`、`POST /notifications/{id}/read`、`POST /notifications/read-all`、`GET /reviews`、`GET /reviews/summary`、`POST /reviews`、`POST /reviews/{id}/helpful` | 登录态用户 | `page/pageSize/type/status`、评价 `resourceType/resourceId` | `unreadCount`、通知字段、评价字段 |

## 完整性检查清单

- [x] 覆盖 `Auth/Captcha/Dashboard/Catalog/Invoke/SDK/Sandbox/Registry/Audit/Grant`
- [x] 覆盖 `DeveloperApplication/DeveloperStatistics/UserActivity/UserSettings`
- [x] 覆盖 `UserMgmt/Monitoring/Health/SystemConfig/Quota/RateLimit/Tag/SensitiveWord/Announcement`
- [x] 覆盖 `Notification/Review/File`

## 按钮到接口绑定卡（关键页面）

| 页面按钮 | 接口 | 成功后 UI | 失败后 UI |
|---|---|---|---|
| `写评论-提交` | `POST /reviews` | 关闭弹窗，刷新评论列表与评分汇总 | 保留输入，提示错误并允许重试 |
| `评论-helpful` | `POST /reviews/{id}/helpful` | 更新该条 helpful 计数 | 恢复按钮态并提示重试 |
| `立即使用` | `POST /catalog/resolve` -> `POST /invoke` | 展示调用结果面板 | 保留请求参数并提供重试 |
| `提交审核` | `POST /resource-center/resources/{id}/submit` | 状态更新为 `pending_review` | 提示状态冲突或权限不足 |
| `审核通过` | `POST /audit/resources/{id}/approve` | 状态更新为 `testing` | 保持当前列表并提示错误 |
| `发布上架` | `POST /audit/resources/{id}/publish` | 状态更新为 `published` | 显示阻断文案或错误提示 |
| ~~新增授权~~ | ~~`POST /resource-grants`~~ | ~~已废弃（2026-04-09 下线）~~；替代方案：API Key + Scope + `published` 状态 | — | — |
| ~~撤销授权~~ | ~~`DELETE /resource-grants/{grantId}`~~ | ~~已废弃（2026-04-09 下线）~~；替代方案：API Key + Scope + `published` 状态 | — | — |

## 3) 全页面到能力域映射（admin + user）

> 说明：不是只覆盖关键页，以下按页面 slug 全量对齐到后端能力域，页面实现时需再展开到本页按钮脚本（见 `03`）和字段落位（见 `04`）。

| 端 | 页面 slug | 能力域 | 对应接口族 |
|---|---|---|---|
| admin | `dashboard/health-check/usage-statistics/data-reports` | 看板与概览 | `/dashboard/*` |
| admin | `agent-list/skill-list/mcp-server-list/app-list/dataset-list/resource-center` | 资源中心列表 | `/resource-center/resources/mine`、`/resource-center/resources/{id}` |
| admin | `agent-register/skill-register/mcp-register/app-register/dataset-register` | 资源注册与编辑 | `POST|PUT /resource-center/resources*` |
| admin | `agent-audit/skill-audit/mcp-audit/app-audit/dataset-audit` | 审核中心 | `/audit/*` |
| admin | ~~`resource-grant-management`~~ | ~~资源授权管理~~ | ~~`/resource-grants*`~~ | ~~已废弃（2026-04-09 下线）~~；替代方案：API Key + Scope + `published` 状态 |
| admin | `user-list/role-management/organization/api-key-management` | 用户组织权限 | `/user-mgmt/*` |
| admin | `developer-applications` | 开发者入驻审批 | `/developer/applications*` |
| admin | `monitoring-overview/call-logs/performance-analysis/alert-management/alert-rules` | 监控告警 | `/monitoring/*` |
| admin | `health-config/circuit-breaker` | 健康与熔断 | `/health/*` |
| admin | `tag-management/security-settings/quota-management/rate-limit-policy/access-control/audit-log/sensitive-words/announcements` | 系统治理 | `/tags*`、`/system-config*`、`/quotas*`、`/rate-limits*`、`/sensitive-words*` |
| admin | `api-docs/sdk-download/api-playground/developer-statistics` | 开发者门户 | `/catalog/*`、`/sdk/v1/*`、`/developer/my-statistics` |
| user | `hub/workspace` | 用户看板与探索 | `/dashboard/explore-hub`、`/dashboard/user-workspace` |
| user | `agent-market/skill-market/mcp-market/app-market/dataset-market` | 五市场检索与详情 | `/catalog/resources*`、`/catalog/resolve`、`/invoke` |
| user | `my-agents-pub/resource-center/agent-list/skill-list/mcp-server-list/app-list/dataset-list` | 我的发布管理 | `/resource-center/resources*` |
| user | `agent-register/skill-register/mcp-register/app-register/dataset-register` | 用户侧资源注册 | `POST|PUT /resource-center/resources*` |
| user | `my-agents/my-skills/authorized-skills/recent-use/my-favorites/usage-records/usage-stats` | 用户活动与资产 | `/user/*` |
| user | `profile/preferences` | 账号资料与设置 | `/auth/*`、`/user-settings/*` |

## 4) Controller 端点附录（代码真值）

> 来源：`src/main/java/**/*Controller.java`。路径均相对 `/api`。  
> 目标：代码里存在的接口能力，文档必须显式可追踪，不遗漏。

| Controller | 端点 |
|---|---|
| `AuthController` | `POST /auth/login`、`POST /auth/register`、`POST /auth/logout`、`GET /auth/me`、`POST /auth/refresh`、`POST /auth/change-password`、`PUT /auth/profile`、`POST /auth/send-sms`、`POST /auth/bind-phone`、`GET /auth/login-history`、`GET /auth/sessions`、`DELETE /auth/sessions/{sessionId}` |
| `CaptchaController` | `GET /captcha/generate`、`POST /captcha/verify` |
| `DashboardController` | `GET /dashboard/admin-overview`、`GET /dashboard/user-workspace`、`GET /dashboard/health-summary`、`GET /dashboard/usage-stats`、`GET /dashboard/data-reports`、`GET /dashboard/explore-hub`、`GET /dashboard/admin-realtime`、`GET /dashboard/user-dashboard` |
| `ResourceCatalogController` | `GET /catalog/resources`、`GET /catalog/resources/trending`、`GET /catalog/resources/search-suggestions`、`GET /catalog/resources/{type}/{id}`、`GET /catalog/resources/{type}/{id}/stats`、`POST /catalog/resolve`、`POST /invoke` |
| `ResourceRegistryController` | `POST /resource-center/resources`、`PUT /resource-center/resources/{id}`、`DELETE /resource-center/resources/{id}`、`POST /resource-center/resources/{id}/submit`、`POST /resource-center/resources/{id}/deprecate`、`GET /resource-center/resources/mine`、`GET /resource-center/resources/{id}`、`POST /resource-center/resources/{id}/versions`、`POST /resource-center/resources/{id}/versions/{version}/switch`、`GET /resource-center/resources/{id}/versions`、`POST /resource-center/resources/{id}/withdraw` |
| `AuditController` | `GET /audit/resources`、`GET /audit/agents`、`GET /audit/skills`、`POST /audit/agents/{id}/approve`、`POST /audit/skills/{id}/approve`、`POST /audit/agents/{id}/reject`、`POST /audit/skills/{id}/reject`、`POST /audit/agents/{id}/publish`、`POST /audit/skills/{id}/publish`、`POST /audit/resources/{id}/approve`、`POST /audit/resources/{id}/reject`、`POST /audit/resources/{id}/publish` |
| ~~`ResourceGrantController`~~ | ~~`POST /resource-grants`、`DELETE /resource-grants/{grantId}`、`GET /resource-grants`~~ | ~~已废弃（2026-04-09 下线）~~；替代方案：API Key + Scope + `published` 状态 |
| `SdkGatewayController` | `GET /sdk/v1/resources`、`GET /sdk/v1/resources/{type}/{id}`、`POST /sdk/v1/resolve`、`POST /sdk/v1/invoke` |
| `SandboxController` | `POST /sandbox/sessions`、`GET /sandbox/sessions/mine`、`POST /sandbox/invoke` |
| `DeveloperApplicationController` | `POST /developer/applications`、`GET /developer/applications/me`、`GET /developer/applications`、`POST /developer/applications/{id}/approve`、`POST /developer/applications/{id}/reject` |
| `DeveloperStatisticsController` | `GET /developer/my-statistics` |
| `UserSettingsController` | `GET /user-settings/workspace`、`PUT /user-settings/workspace`、`GET /user-settings/api-keys`、`POST /user-settings/api-keys`、`DELETE /user-settings/api-keys/{id}`、`GET /user-settings/stats` |
| `UserActivityController` | `GET /user/usage-records`、`GET /user/favorites`、`POST /user/favorites`、`DELETE /user/favorites/{id}`、`GET /user/usage-stats`、`GET /user/my-agents`、`GET /user/my-skills`、`GET /user/authorized-skills`、`GET /user/recent-use` |
| `UserMgmtController` | `GET|POST /user-mgmt/users`、`GET|PUT|DELETE /user-mgmt/users/{id}`、`GET|POST /user-mgmt/roles`、`PUT|DELETE /user-mgmt/roles/{id}`、`GET|POST /user-mgmt/api-keys`、`PATCH /user-mgmt/api-keys/{id}/revoke`、`GET /user-mgmt/org-tree`、`POST /user-mgmt/orgs`、`GET /user-mgmt/orgs/{id}`、`PUT|DELETE /user-mgmt/orgs/{id}`、`GET|PUT|DELETE /user-mgmt/users/{id}/org`、`GET|POST|PUT /user-mgmt/users/{id}/roles`、`DELETE /user-mgmt/users/{id}/roles/{roleId}` |
| `MonitoringController` | `GET /monitoring/kpis`、`GET /monitoring/performance`、`GET /monitoring/call-logs`、`GET /monitoring/alerts`、`GET /monitoring/traces`、`POST /monitoring/alert-rules`、`PUT /monitoring/alert-rules/{id}`、`DELETE /monitoring/alert-rules/{id}`、`GET /monitoring/alert-rules/{id}`、`GET /monitoring/alert-rules`、`POST /monitoring/alert-rules/{id}/dry-run` |
| `HealthController` | `GET /health/configs`、`POST /health/configs`、`PUT /health/configs/{id}`、`DELETE /health/configs/{id}`、`GET /health/circuit-breakers`、`PUT /health/circuit-breakers/{id}`、`POST /health/circuit-breakers/{id}/break`、`POST /health/circuit-breakers/{id}/recover`、`GET /health/security-config` |
| `SystemParamController` | `GET|PUT /system-config/params`、`GET|PUT /system-config/security`、`GET /system-config/audit-logs`、`POST /system-config/network/apply`、`POST /system-config/acl/publish` |
| `RateLimitRuleController` | `POST /system-config/rate-limits`、`PUT /system-config/rate-limits/{id}`、`DELETE /system-config/rate-limits/{id}`、`GET /system-config/rate-limits/{id}`、`GET /system-config/rate-limits` |
| `QuotaController` | `POST /quotas`、`PUT /quotas`、`DELETE /quotas/{id}`、`GET /quotas/{id}`、`GET /quotas` |
| `QuotaRateLimitController` | `POST /rate-limits`、`DELETE /rate-limits/{id}`、`GET /rate-limits/{id}`、`GET /rate-limits`、`PATCH /rate-limits/{id}` |
| `TagController` | `GET /tags`、`POST /tags`、`POST /tags/batch`、`PUT /tags/{id}`、`DELETE /tags/{id}` |
| `SensitiveWordController` | `GET /sensitive-words`、`GET /sensitive-words/categories`、`GET /sensitive-words/count`、`POST /sensitive-words`、`POST /sensitive-words/batch`、`PUT /sensitive-words/{id}`、`DELETE /sensitive-words/{id}`、`POST /sensitive-words/check` |
| `AnnouncementController` | `GET /system-config/announcements`、`POST /system-config/announcements`、`PUT /system-config/announcements/{id}`、`DELETE /system-config/announcements/{id}` |
| `NotificationController` | `GET /notifications`、`GET /notifications/unread-count`、`POST /notifications/{id}/read`、`POST /notifications/read-all` |
| `ReviewController` | `GET /reviews`、`GET /reviews/summary`、`POST /reviews`、`POST /reviews/{id}/helpful` |
| `FileController` | `POST /files/upload` |
