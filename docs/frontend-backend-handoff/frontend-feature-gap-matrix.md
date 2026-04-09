# 前端功能缺口矩阵（路由 × API × 筛选 × CRUD）

**handoff 目录入口**：[README.md](./README.md)｜[接口/检索清单](./01-backend-api-and-query-checklist.md)｜[枚举对齐](./02-dropdown-enums-alignment.md)

与 [管理端列表 UI 巡检与后端接口建议](./frontend-management-ui-audit-and-backend-api-requests.md) 互补：本文档按**侧栏 / 路由**列出主要页面的数据流形态与缺口；**接线状态**会随前端实现更新。

**图例**

| 标记 | 含义 |
|------|------|
| `serverPaged` | 列表分页与筛选主要由服务端完成 |
| `serverPage_clientFilter` | 已请求分页，但部分筛选仍在浏览器内存（待收敛） |
| `clientOnly` | 全量或单页数据在浏览器内过滤 / 分页 |
| `stub` | 占位或未接真实读接口 |

---

## 管理端 · 总览 / 资源（`admin`）

| 路由 page | 主要组件 | 列表/数据 API | 数据流 | 备注 |
|-----------|----------|---------------|--------|------|
| dashboard | Overview 等 | `dashboard.service` | 混合 | 视卡片而定 |
| health-check | 健康看板 | 多种 | serverPaged | — |
| usage-statistics | 用量统计 | `user-activity` / `dashboard` | 混合 | — |
| data-reports | 数据报表 | `dashboard` 等 | 混合 | — |
| agent-list / skill-list / mcp-server-list / app-list / dataset-list | 资源列表 | `agent` / `skill` / `smart-app` / `dataset` + `keyword` | serverPaged | 行内操作图标风见审计文档 |
| *-register / *-detail | 注册与详情 | 各 resource API | CRUD | — |
| *-audit | ResourceAuditList | `resourceAuditService` | serverPaged | 支持 keyword |
| provider-list / provider-create | ProviderManagementPage | `providerService` | serverPaged | — |

---

## 管理端 · 用户与权限

| 路由 page | 主要组件 | 列表/数据 API | 数据流 | 备注 |
|-----------|----------|---------------|--------|------|
| user-list / role-management / api-key-management | UserManagementModule | `userMgmtService` 等 | serverPaged | — |
| ~~resource-grant-management~~ | （宜移除） | — | — | **后端 `/resource-grants*` 已删**；勿再视作 serverPaged 真值 |
| organization | OrgStructurePage | `getOrgTree` + `createOrg` / `updateOrg` / `deleteOrg` | 树 CRUD | 表单 Modal 改善父节点选择 |
| ~~grant-applications~~ | （宜移除） | — | — | **`/grant-applications*` 已删** |
| developer-applications | DeveloperApplicationListPage | `developerApplicationService.list` + **`keyword`** | serverPaged | 防抖请求 |

---

## 管理端 · 监控

| 路由 page | 主要组件 | 列表/数据 API | 数据流 | 备注 |
|-----------|----------|---------------|--------|------|
| monitoring-overview | MonitoringOverviewPage | `monitoringService.getKpis` 等 | serverPaged | — |
| call-logs | CallLogPage | `listCallLogs` + **keyword/status** | serverPaged | 后端 query：`status`（`all` 不筛选） |
| performance-analysis | PerformanceAnalysisPage | `getPerformanceMetrics` | serverPaged | `PerformanceMetric.service` 优先分桶 |
| alert-management | AlertMgmtPage | `listAlerts` + **keyword/severity/alertStatus** | serverPaged | 后端 query：`severity`、`alertStatus`（`all` 不筛状态） |
| alert-rules | AlertRulesPage | `listAlertRules` + **create / update / delete** | clientFilter | 列表关键词+级别筛为客户端；编辑/删除已接线 |
| health-config | HealthConfigPage | `healthService` | CRUD | — |
| circuit-breaker | CircuitBreakerPage | `healthService` | CRUD | 工具栏检索+状态筛（客户端） |

---

## 管理端 · 系统配置

| 路由 page | 主要组件 | 列表/数据 API | 数据流 | 备注 |
|-----------|----------|---------------|--------|------|
| category-management | CategoryManagement | `categoryService` | CRUD | 工具栏树检索（客户端） |
| tag-management | TagManagementPage | `tagService` | CRUD | — |
| ~~model-config~~（已移除） / rate-limit-policy | RateLimitPage | `systemConfigService` | CRUD（限流） | — |
| security-settings / 系统参数 | SystemConfigExtraPages | `getSecurity` / `getParams` | CRUD | — |
| quota-management | **QuotaManagementPage** | `quotaService`（配额 **update**、限流 **delete**） | clientFilter | 工具栏 keyword + 范围/目标类型；限流无 update API |
| access-control | AccessControlPage | **`getAclRules`** + `publishAcl` | serverPaged + 写 | 曾用本地假数据初始化，已改为拉取 `/system-config/acl` |
| audit-log | AuditLogPage | `listAuditLogs` + **keyword / action / result** | serverPaged | 导出 CSV 为当前页数据 |
| sensitive-words | SensitiveWordPage | `sensitiveWordService` + **keyword / category / enabled** | serverPaged | 行内编辑（分类/severity/启停）；词面改需后端扩展 PUT |
| announcements | AnnouncementPage | `listAnnouncements` + **keyword / type** | serverPaged | — |

**未接入路由的组件**：`SystemConfigExtraPages.tsx` 内 `SystemQuotaPage` 为简化单表单，**未被** `SystemConfigModule` 引用；避免与 `QuotaManagementPage` 混淆。

---

## 用户控制台（`user`）摘要

| 区域 | 典型数据流 | 备注 |
|------|------------|------|
| hub / workspace / market* | `catalog` / `explore` | 多为 serverPaged + 少量客户端 tag 过滤 |
| resource-center | ResourceCenterManagementPage | serverPaged + keyword |
| ~~my-grant-applications~~ | （宜移除） | **`GET /grant-applications/mine` 已删** |
| api-key-management（子项） | ApiKeyListPage 等、`listApiKeys` | serverPaged |
| token 管理（若侧栏可达） | TokenListPage：`GET /user-mgmt/tokens`、`PATCH /user-mgmt/tokens/{id}/revoke` | serverPaged；`keyword`、`status`（`all` / `expired` 等） |

---

## 后端待确认路径（与当前前端对齐）

以下路径以前端调用为准，若后端不同请改 `src/api/services/*.ts` 一处即可：

- 审计日志扩展 query：`keyword`、`result`（`failure` / `success`，与 `onlyFailure` 并存时以 `result` 为准）、已有 `action`
- 调用日志：`keyword`、`status`；告警列表：`keyword`、`severity`、`alertStatus`
- ACL：`GET /system-config/acl` 返回规则数组或 `{ rules: [...] }`
- Token：已实现 `GET /user-mgmt/tokens`（分页、`keyword`、`status`）、`PATCH /user-mgmt/tokens/{id}/revoke`

---

## 维护

更新实现后请同步修改本文档对应行的「数据流」列，并在 [审计文档](./frontend-management-ui-audit-and-backend-api-requests.md) 顶部「前端接线状态」段落下备注日期。

| 日期 | 说明 |
|------|------|
| 2026-03-30 | 已接线：公告/入驻/审计关键词与筛选；监控调用日志与告警 query；Token 分页与撤销；ACL GET；性能指标 `service` 字段优先；组织架构 Modal 创建/编辑。 |
| 2026-03-30 | 配额/限流工具栏与配额编辑、限流删除；熔断/分类/标签/告警规则检索与规则改删；健康检查/熔断行内「编辑」胶囊。 |
| 2026-03-30 | 后端：`/monitoring` 分页 query 补 `status`/`severity`/`alertStatus`；审计 `result`；`/user-mgmt/tokens` 与撤销；等。 |
| 2026-04-09 | Grant / grant-applications / `/resource-grants*` 删除；用户与权限矩阵中 grant 相关行标为下线。 |
