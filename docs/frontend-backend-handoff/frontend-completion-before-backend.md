# 前端补全优先 · 与后端对接分工（整合说明）

本文档汇总 **管理端** 在「先把前端交互与请求形状补全，再让后端落库与检索」策略下的状态，避免各页零散说明。

**同目录索引**：[README.md](./README.md)（handoff 总览）｜[01 发给后端的文件与检索清单](./01-backend-api-and-query-checklist.md)｜[02 下拉与枚举对齐](./02-dropdown-enums-alignment.md)。更细的接口巡检见 [frontend-management-ui-audit-and-backend-api-requests.md](./frontend-management-ui-audit-and-backend-api-requests.md) 与 [frontend-feature-gap-matrix.md](./frontend-feature-gap-matrix.md)。

---

## 1. 原则

| 层级 | 责任 |
|------|------|
| **前端** | 列表工具栏具备与业务匹配的 **检索 / 筛选 / 分页请求参数**；表格行具备 **基础 CRUD 或等价操作**（查看、编辑、删除、启停等）；请求体与 query 与 `src/types/dto/` 及 `*.service.ts` 对齐。 |
| **后端** | 对应用 query **在服务端过滤并返回与 `total` 一致的语义**；PUT/PATCH 体字段与 OpenAPI 或对齐 spec 一致；若不支持某参数，应明确返回或文档说明，避免前端误以为「已筛选全库」。 |

---

## 2. 敏感词管理（本次已补前端）

**页面**：`src/views/systemConfig/SensitiveWordPage.tsx`  
**服务**：`src/api/services/sensitive-word.service.ts`

| 能力 | 前端状态 | 后端需确认 / 建议 |
|------|----------|-------------------|
| 关键词检索 | 工具栏搜索框；`GET /sensitive-words` 传 `keyword`（可选） | 若当前网关未识别 `keyword`，请增加对 `word`（或约定字段）的模糊匹配，并使分页 `total` 为筛选后总数。 |
| 分类筛选 | `LantuSelect`；传 `category`（已有） | 与现有实现一致即可。 |
| 启用/禁用筛选 | 传 `enabled`（已有） | 与现有实现一致即可。 |
| 编辑 | 「编辑」胶囊按钮；弹窗可改 **分类、severity、启用**；调用 `PUT /sensitive-words/{id}` | `SensitiveWordUpdateRequest` 当前 **不含 `word`**；若产品要求改词面，请扩展后端 Update 与 DTO 后再接表单项。 |
| 删除 / 行内启停 | 已有 | 不变。 |

分类下拉的选项来源：列表拉取成功后顺带请求 `GET /sensitive-words/categories` 刷新，与筛选条件联动。

---

## 2.1 其他列表页（同轮已补前端交互）

| 页面 | 检索 / 筛选 | 行内 / 弹窗 CRUD 说明 |
|------|-------------|----------------------|
| **配额管理** `QuotaManagementPage` | 配额 tab / 限流 tab 各一套工具栏：`keyword` 本地筛名称；配额加「范围」下拉；限流加「目标类型」下拉 | 配额行 **编辑**（`PUT /quotas` 已有 `updateQuota`: 日/月上限、非全局对象名）；限流行 **删除**（`DELETE /rate-limits/{id}`）+ 原有启停开关 |
| **熔断降级** `CircuitBreakerPage` | `MgmtPageShell` 工具栏：名称/降级 Agent 搜索 + 状态下拉（客户端过滤） | **编辑** 胶囊（原「配置」）；手动熔断/恢复仍为图标快捷按钮 |
| **健康检查** `HealthConfigPage` | 已有工具栏 | 行内 **编辑** 胶囊（原「配置」文案） |
| **告警规则** `AlertRulesPage` | 规则名/指标关键词 + 严重级别筛选（客户端） | **编辑** 弹窗（`PUT /monitoring/alert-rules/{id}`）、**删除**（`DELETE`）、保留 **试跑** |
| **分类管理** `CategoryManagement` | 工具栏搜索：树剪枝过滤（名称/编码） | 顺带修复若干乱码提示文案 |
| **标签管理** `TagManagementPage` | 搜索框与分类 chips 单行/窄屏可横滑 | 原有增删与筛选逻辑不变 |
| **资源授权** `ResourceGrantManagementPage` | 列表区：按 grantee **Key ID / 前缀** 筛选（**仅当前页**数据；全库检索需后端 `keyword` query） | 原有撤销不变 |

**说明**：上述除资源授权外，筛选均在 **已拉取列表** 上做客户端过滤；数据量大时请后端为对应 `GET` 增加 query，并把筛选迁到服务端以保证分页 `total` 一致。

---

## 3. 全站管理页：仍建议巡检的类别

- **用户控制台** 大图列表（`UserListPage` / `TokenListPage` / `ApiKeyListPage` / `RoleListPage`）：已有搜索或过滤，与管理壳 `TOOLBAR_ROW_LIST` 未强制统一。
- **控制台资源列表**（Agent/App/Dataset/Skill 与双审核台）：列表级 `keyword` 已接后端；行内仍以 **图标** 为主，若需与 Mgmt 胶囊统一需单独立项。

---

## 4. 变更记录

| 日期 | 说明 |
|------|------|
| 2026-03-30 | 新增：敏感词页工具栏检索 + 分类/状态筛选 + 编辑弹窗；`list` 增加 `keyword` query；本整合文档首版。 |
| 2026-03-30 | 扩容：配额/限流、熔断、健康检查、告警规则、分类、标签、资源授权等列表检索或 CRUD 补全；`useMonitoring` 增加告警规则更新/删除 mutations。 |
