# 发给后端：应对照的前端文件 + 检索/接口补充清单

把本文与 `**src/api/services` 全量 `.ts`、`src/types/dto` 全量 `.ts**` 一并提供给后端，可快速对齐路径、query 字段与响应形状。以下为**高优先级**拆分说明。

> **2026-04-09**：`/grant-applications*`、`/resource-grants*`（除占位 `GET .../api-keys/.../resource-grants` 空列表外）**已删除**。下表若仍列出 `grant-application` / `resource-grant` 相关文件，仅代表前端仓库可能遗留，**不再作为后端待实现项**。

---

## 1. 建议后端重点打开的前端文件（按职责）

### 1.1 必带：HTTP 契约（路径 + query + body 意图）

以下目录下**每个文件**都可能包含路径常量和 `params` 形状，后端应能一一映射到 OpenAPI：


| 路径                      | 说明                                      |
| ----------------------- | --------------------------------------- |
| `src/api/services/*.ts` | 全部 33 个服务模块（见仓库 `src/api/services/` 目录） |


与**列表检索/分页**强相关的典型文件（后端常需补 query 或确认语义）：


| 文件                                                                                | 主要接口关注点                                                                                    |
| --------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------ |
| `sensitive-word.service.ts`                                                       | `GET /sensitive-words`：`keyword`、`category`、`enabled`、`page`、`pageSize`；PUT 是否扩展 `word`    |
| `system-config.service.ts`                                                        | `listAuditLogs`：`keyword`、`action`、`result`；`listAnnouncements`：`keyword`、`type`           |
| ~~`grant-application.service.ts`~~                                                | **接口已删除**；前端应移除或 stub                                                     |
| `developer-application.service.ts`                                                | `GET /developer/applications`：`status`、`keyword`、分页                                        |
| `monitoring.service.ts`                                                           | `listCallLogs`：`keyword`、`status`；`listAlerts`：`keyword`、`severity`、`alertStatus`          |
| `user-mgmt.service.ts`                                                            | `listUsers`、`listTokens` 等的 `keyword`、`status`、分页                                          |
| ~~`resource-grant.service.ts`~~                                                   | **`GET /resource-grants` 已删除**；勿再向后端提需求                                            |
| `resource-audit.service.ts`                                                       | 审核列表 `keyword`、`status`、`resourceType` 等（以类型为准）                                            |
| `quota.service.ts`                                                                | `listQuotas` / `listRateLimits`：前端列表多为客户端筛选；大表时建议服务端 `keyword`、`subjectType` 等             |
| `provider.service.ts`                                                             | 列表 `keyword`（实现里可能映射为 `name`）/`status`                                                     |
| `agent.service.ts`、`skill.service.ts`、`dataset.service.ts`、`smart-app.service.ts` | 各自 `list` 的 `keyword`、`status`、标签等                                                         |


### 1.2 必带：类型与枚举（与 OpenAPI enum 对齐）


| 路径                   | 说明                                |
| -------------------- | --------------------------------- |
| `src/types/dto/*.ts` | 全部 DTO；请求/响应字段与字面量联合类型以后端实现为准反向核对 |


### 1.3 按需：页面如何传筛选（仅当后端要对照 UI 行为）


| 区域        | 代表性页面路径                                                                                                 |
| --------- | ------------------------------------------------------------------------------------------------------- |
| 系统配置 / 监控 | `src/views/systemConfig/*`、`src/views/monitoring/*`                                                     |
| 用户与权限     | `src/views/userMgmt/*`                                                                                  |
| 审核 / 资源   | `src/views/audit/*`、`src/views/resourceCenter/*`                                                        |
| 控制台资源列表   | `src/views/agent/*`、`src/views/skill/*`、`src/views/apps/*`、`src/views/dataset/*`、`src/views/provider/*` |


---

## 2. 检索条件与语义：后端待确认项（摘要）

详细分接口说明见同目录 **[frontend-management-ui-audit-and-backend-api-requests.md](./frontend-management-ui-audit-and-backend-api-requests.md)**；矩阵见 **[frontend-feature-gap-matrix.md](./frontend-feature-gap-matrix.md)**。


| 能力          | 前端行为                                              | 后端建议                                      |
| ----------- | ------------------------------------------------- | ----------------------------------------- |
| 敏感词列表       | 传 `keyword`、`category`、`enabled`                  | 实现服务端过滤；`total` = 过滤后总数；PUT 按需支持改词 `word` |
| 公告列表        | 传 `keyword`、`type`                                | 同上                                        |
| 审计日志        | 传 `keyword`、`action`、`result`                     | 同上；`action` 合法值建议出字典                      |
| 调用日志 / 告警   | 传 `keyword`、`status` 或 `severity` / `alertStatus` | 与前端 `monitoring.service` 类型一致             |
| ~~授权申请~~ / 入驻申请 | 入驻：`keyword`、`status`                              | **授权申请 API 已删**；仅入驻保留                        |
| Token 列表    | `keyword`、`status`                                | 见 `user-mgmt.service`                     |
| ~~资源授权列表~~      | —                                                         | **接口已删**                                       |
| 配额 / 限流列表   | 现多在前端内存筛选                                         | 数据量大时补 query 与索引                          |


---

## 3. 给后端的「最小交付包」清单（可复制到工单）

1. 本文档（`01-backend-api-and-query-checklist.md`）
2. `02-dropdown-enums-alignment.md`（枚举对齐）
3. `frontend-feature-gap-matrix.md`
4. `frontend-management-ui-audit-and-backend-api-requests.md`
5. 压缩包或仓库路径：`src/api/services/`、`src/types/dto/`（或整仓前端）

---

## 4. 变更记录


| 日期         | 说明                           |
| ---------- | ---------------------------- |
| 2026-03-30 | 初版：集中 handoff 目录；列服务文件与检索摘要。 |
| 2026-03-30 | 后端对齐：`GET /monitoring/call-logs` 增加 `status`；`/alerts` 增加 `severity`、`alertStatus`；审计日志 query 增加 `result`；`GET /user-mgmt/tokens` 分页+`keyword`+`status`、`PATCH /user-mgmt/tokens/{id}/revoke`；等。 |
| 2026-04-09 | Grant / grant-applications / `/resource-grants*` 删除；§1.1 与 §2 相关行标为下线。 |


