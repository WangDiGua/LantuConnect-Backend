# User 角色前端页面与后端全量对齐审查 — 2026-04

**节奏**：Superpowers（`using-superpowers`、`executing-plans`、`verification-before-completion`）+ 项目 [lantuconnect-backend-audit](../../.cursor/skills/lantuconnect-backend-audit/SKILL.md) + 个人技能（`backend-capability-closure-audit`、`rbac-api-permissions`、`api-contract-and-dto-audit`、`data-display-clarity-audit`、`prd`、`frontend-design`）。

**范围**：前端仓库 `LantuConnect-Frontend` 中 Hash 路由 **`/#/user/:page`** 侧栏登记页面、`src/constants/consoleRoutes.ts` 内 `USER_SIDEBAR_PAGES` 全表 slug，以及重定向/规范化后的等价体验。HTTP 路径相对前端的 `VITE_API_BASE_URL`（若与手册一致可加 `/api` 再叠加 `context-path`，以环境变量为准）。

---

## 1. 全量页面清单（slug → 组件 → Placeholder）

| page slug | 侧栏分组（navigation） | 实际组件（MainLayout `MainContent`） | Placeholder? | 备注 |
|-----------|------------------------|----------------------------------------|--------------|------|
| `hub` | 探索发现 | `ExploreHub` | 否 | `dashboardService` → `/dashboard/explore-hub` 等 |
| `workspace` | 我的工作台 → 工作台总览 | `UserWorkspaceOverview` | 否 | `dashboardService` → `/dashboard/user-workspace` |
| `developer-onboarding` | 我的工作台 | `DeveloperOnboardingPage` | 否 | `authService`、`developerApplicationService` → `/developer/applications` |
| `my-favorites` | 我的工作台 | `MyFavoritesPage` | 否 | `userActivityService` → `/user/favorites` |
| `authorized-skills` | 我的工作台 → 已授权技能 | `AuthorizedSkillsPage` | 否 | `/user/authorized-skills` |
| `quick-access` | 同上登记 | `QuickAccess` | 否 | 以导航为主，可链到 hub/docs |
| `resource-market` | 资源与资产 → 浏览 | `UserResourceMarketHub` | 否 | 内嵌五类 `*Market` 组件 |
| `agent-market` | 同上（legacy slug） | （重定向） | — | → `resource-market?tab=agent`（`MainLayout` useEffect + `USER_LEGACY_MARKET_PAGE_TO_TAB`） |
| `skill-market` | 同上 | （重定向） | — | → `?tab=skill` |
| `mcp-market` | 同上 | （重定向） | — | → `?tab=mcp` |
| `app-market` | 同上 | （重定向） | — | → `?tab=app` |
| `dataset-market` | 同上 | （重定向） | — | → `?tab=dataset` |
| `my-agents-pub` | 我的发布 | `MyPublishHubPage` | 否 | `userActivityService` `/user/my-agents`、`/user/my-skills` 等 |
| `resource-center` | 我的发布 | `ResourceCenterManagementPage` | 否 | `resourceCenterService`、`resourceAuditService` |
| `agent-list` … `dataset-list` | legacy | （重定向） | — | → `resource-center?type=`（`LEGACY_USER_RESOURCE_PAGES`） |
| `agent-register` … `dataset-register` | 隐式从资源中心进入 | `renderResourceRegister(type)` | 否 | `ResourceRegisterPage` + `resourceCenterService` |
| `usage-records` | 使用 | `UsageRecordsPage` | 否 | `/user/usage-records` |
| `recent-use` | 未在主导航分组出现时可路由 | `UsageRecordsPage` initialView=recent | 否 | `/user/recent-use` |
| `usage-stats` | 使用 | `UsageStatsPage` | 否 | `/user/usage-stats` |
| ~~`grant-applications`~~ | 授权 | ~~`GrantApplicationListPage`~~ | — | **已下线**；`/grant-applications*` **已删**，宜移除侧栏 |
| ~~`my-grant-applications`~~ | 授权 | ~~`MyGrantApplicationsPage`~~ | — | 同上 |
| `api-docs` | 开发者中心 | `ApiDocsPage` | 否 | 以静态/内嵌文档为主 |
| `sdk-download` | 开发者中心 | `SdkDownloadPage` | 否 | 静态为主 |
| `api-playground` | 开发者中心 | `ApiPlaygroundPage` | 否 | 通用 `http.*` 调网关路径 |
| `developer-statistics` | 开发者中心 | `DeveloperStatsPage` | 否 | `/developer/my-statistics`、`/dashboard/owner-resource-stats` |
| `profile` | 个人设置 | `UserSettingsHubPage` tab=profile | 否 | `user-settings`、用户接口 |
| `preferences` | 个人设置 | `UserSettingsHubPage` tab=preferences | 否 | 同上 |

**结论**：`USER_SIDEBAR_PAGES` 中列出的 user slug 均在 `LantuConnect-Frontend/src/layouts/MainLayout.tsx`（约 L325–L419）有 **`case`，无落入 `PlaceholderView` 的登记页**。规范化入口 `normalizeDeprecatedPage` 将 `submit-agent` / `submit-skill` / `my-agents` 等合并到 `my-agents-pub` 等价体验。

---

## 2. 页面 → 主要 HTTP（前端 `src/api/services` + `lib/http`）

| 页面/场景 | 代表服务方法 | 路径前缀（相对 baseURL） |
|-----------|--------------|---------------------------|
| 探索 hub / 工作台 | `dashboardService` | `/dashboard/explore-hub`、`/dashboard/user-workspace`、`/dashboard/user-dashboard` |
| 统一资源市场（五 tab） | `resourceCatalogService` + 各 `*Market` | `/catalog/resources`、`/catalog/resources/trending`、`/catalog/resources/search-suggestions`、`getByTypeAndId`、`/stats`；市场内可能 `agentService` 等 |
| 资源中心 / 注册 | `resourceCenterService` | `/resource-center/resources/mine`、`/resource-center/resources`、`submit`、`deprecate`、`withdraw`、`versions`、`skill-artifact`、分片上传等 |
| 审核（用户侧若触达退回理由等） | `resourceAuditService` | `/audit/resources` |
| 收藏 / 用量 / 授权技能 / 最近 | `userActivityService` | `/user/favorites`、`/user/usage-records`、`/user/usage-stats`、`/user/authorized-skills`、`/user/recent-use`、`/user/my-agents`、`/user/my-skills` |
| 授权工单 | `grantApplicationService` | `POST /grant-applications`、`GET /mine`、`GET /pending`、`POST .../approve|reject` |
| 评价 | `review.service` | `/reviews`、`/reviews/page`、`/reviews/summary`、`POST .../helpful` |
| 网关调用 | `invokeService` / `sdkService` | `POST /invoke`；`GET/POST /sdk/v1/...` |
| 开发者统计 | `developerStatsService` | `GET /developer/my-statistics`、`GET /dashboard/owner-resource-stats` |
| 个人设置 / Key | `userSettingsService` | `/user-settings/workspace`、`/user-settings/api-keys`、`/user-settings/stats` |
| 入驻 | `developerApplicationService` | `/developer/applications`（见控制器） |
| Playground | `http` 直连 | `/catalog/*`、`/reviews/page`、`/catalog/resolve`、`/invoke` 等 |

---

## 3. 后端对账（存在性 + 行为要点）

| 前端路径 | 后端控制器（节选） | 对齐结论 |
|----------|-------------------|----------|
| `/catalog/resources` 等 | [`ResourceCatalogController`](../../src/main/java/com/lantu/connect/gateway/controller/ResourceCatalogController.java) + `UnifiedGatewayServiceImpl` | 已对齐 |
| `/invoke` | 同上 | 已对齐；skill 禁止 invoke 由后端校验 |
| `/resource-center/*` | `ResourceRegistryController` / resource-center 模块（与 [`ResourceCenter`](../../src/main/java/com/lantu/connect/gateway/controller/ResourceRegistryController.java) 一致） | 已对齐（具体方法以实现为准） |
| `/audit/resources` | `AuditController` | 已对齐 |
| `/user/*` | [`UserActivityController`](../../src/main/java/com/lantu/connect/useractivity/controller/UserActivityController.java) `@RequestMapping("/user")` | 已对齐 |
| `/grant-applications/*` | [`GrantApplicationController`](../../src/main/java/com/lantu/connect/gateway/controller/GrantApplicationController.java) | 已对齐；`pending` 对非 reviewer 仅展示 **本人拥有资源** 上的申请（`GrantApplicationServiceImpl#applyPendingScopeForReviewer`） |
| `/reviews/*` | `ReviewController` | 已对齐 |
| `/developer/my-statistics` | [`DeveloperStatisticsController`](../../src/main/java/com/lantu/connect/onboarding/controller/DeveloperStatisticsController.java) `@RequestMapping("/developer")` | 已对齐 |
| `/dashboard/owner-resource-stats` | [`DashboardController`](../../src/main/java/com/lantu/connect/dashboard/controller/DashboardController.java) | 已对齐 |

**前端有、后端必须有**：本轮抽查未发现「前端主路径指向完全不存在的路由」；差异主要在 **权限与数据是否为空**（见 §5）。

---

## 4. 权限与菜单（RBAC 注意点）

- 用户侧栏子项经 `subItemMeetsPermission`（`MainLayout.tsx`）：仅在 `SUB_ITEM_PERM_MAP` 出现的 id 才校验；**`grant-applications` 不在 map 中** → 所有登录用户菜单上均可见「授权审批待办」，与后端「owner 可见名下资源待办」一致，**普通用户无发布资源时列表可为空**（非 403 断裂）。
- 「我的发布」分组 `requiresPublish`：无 `agent|skill|mcp|app|dataset:create` 之一时整组隐藏 `filteredSubGroupsForSidebarId` — 与产品预期一致。
- `developer-onboarding`：对 `developer` / `platform_admin` / `reviewer` 隐藏，避免与身份重复。

---

## 5. 产品细度（PRD + data-display）

| 主题 | 现状 | 缺口等级 |
|------|------|----------|
| 列表作者 / 评分摘要 | 后端 `ResourceCatalogItemVO` 已含 `createdBy`、`createdByName`、`ratingAvg`、`reviewCount`；前端 `resource-catalog.service.ts` 的 `normalizeCatalogItem` 已映射 | **已落地**：`agent`/`skill`/`app` 的 service `to*` 与 Agent/Skill/App 市场卡片、详情补齐展示（与 dataset/MCP 模式对齐） |
| 详情与评论 | 详情 + `/stats` + `/reviews/page` 多请求属已知架构；契约见 [public-catalog-contract.md](../api/public-catalog-contract.md) | **P2** 市场详情抽屉若未拉 stats/reviews 则口碑弱 |
| 五类差异 | skill 不可 invoke、dataset 元数据、app launch：后端已约束 | **已部分落地**：`ApiPlaygroundPage` 页眉补充 skill 勿走 invoke、须 Key 与 Grant；市场页原有 tagline 保留 |
| 开发者统计 | `/developer/my-statistics` 与 `owner-resource-stats` 双源 | **P2** 前端可加强说明两区块数据源差异（见 `developer-stats.service.ts` 注释） |
| `authorized-skills` | 路由与后端 `/user/authorized-skills` 存在，`AuthorizedSkillVO` 字段与页面已对齐 | **已落地**：侧栏「我的工作台」展示入口；列表映射 `source`/`agentName`/`packFormat`/`lastUsedTime`；深链技能市场 |

---

## 6. P0 / P1 / P2 汇总

| 级别 | 项 | 建议动作 |
|------|-----|----------|
| **P0** | 无 | 本轮未发现登记页 Placeholder 或主干 API 404 映射缺失 |
| **P1** | 网关/资源类型差异的用户可感知文案 | **已部分落地**：Playground 页眉；完整「错误码→帮助链接」可后续迭代 |
| **P1** | `grant-applications` 对无资源 owner 的空列表 | **已落地**：空态说明 + 页眉描述与后端 `pagePendingApplications` 范围一致 |
| **P2** | `authorized-skills` 入口 | **已落地**（见 §8） |
| **P2** | 列表字段曝光一致性 | **已落地**：五类市场卡片**恒定展示**创建者 + 目录评分·评论数（无数据时 — / 0）；`UserResourceMarketHub` 顶栏说明与内嵌页一致 |

---

## 7. 验证（verification-before-completion）

| 检查项 | 结果 |
|--------|------|
| `USER_SIDEBAR_PAGES` slug 与 `MainLayout` user 分支 `case` 全覆盖 | **通过**（见 §1） |
| 主干 API 与后端 Controller 映射 | **通过**（见 §3） |
| `mvn test`（后端回归） | **已通过**（2026-04-03） |
| `npm run build`（前端） | **已通过**（2026-04-03） |

---

## 8. 代码交付记录（2026-04-03）

| 仓库 | 变更 |
|------|------|
| LantuConnect-Backend | `GrantApplicationServiceImpl#pagePendingApplications`：`status` 为 `all` 或省略时不再错误地锁死为 `pending`，与前端「全部状态」一致 |
| LantuConnect-Frontend | `navigation`：`USER_WORKSPACE_GROUPS` 增加「已授权技能」 |
| LantuConnect-Frontend | `agent`/`skill`/`smart-app` DTO 与 `*.service.ts` 的 `to*`：透传 `createdBy*`、`ratingAvg`、`reviewCount` |
| LantuConnect-Frontend | `AgentMarket` / `SkillMarket` / `AppMarket`：卡片与详情展示创建者、目录评分与评论数摘要 |
| LantuConnect-Frontend | `AuthorizedSkillsPage`、`user-activity`：与 `AuthorizedSkillVO` 字段对齐；技能市场深链 |
| LantuConnect-Frontend | `GrantApplicationListPage`：页眉/空态/请求参数 `status=all` |
| LantuConnect-Frontend | `UserResourceMarketHub`：说明文案；五类 `*Market` 列表统一恒定展示作者+评分评论数；`AgentMarket` 卡片增加 `reviewCount`；`ApiPlaygroundPage` 技能/Key/Grant 提示 |

---

## 9. 维护建议

- 新增用户侧路由时：**同时**更新前端 `consoleRoutes.ts` 的 `USER_SIDEBAR_PAGES` 与 `MainLayout.tsx` 的 `case`，避免 `findSidebarForPage` 判无效。
- 双仓对齐说明可链至本文与本仓库 [frontend-alignment-handbook.md](../frontend-alignment-handbook.md)。
