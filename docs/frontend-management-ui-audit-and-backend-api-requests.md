# 管理端列表 UI 巡检与后端接口补充建议

本文档基于 2026-03 前后对「管理列表：双标题、胶囊行内操作、工具栏单行」相关改动的**全面代码巡检**结果整理，供后端评估接口与检索能力是否需要补强。

---

## 1. 前端实现状态（对照原规划）

### 1.1 已落实（管理配置 / 用户权限等主线）

| 能力 | 说明 |
|------|------|
| **MgmtPageShell 双标题** | `hasSecondarySidebar` 时壳内不再渲染可见粗体标题，仅图标 + 描述；`h1.sr-only` 承载完整标题与描述（见 `src/views/userMgmt/MgmtPageShell.tsx`）。 |
| **行内操作样式** | `mgmtTableActionGhost` / `Positive` / `Danger` 为圆角胶囊、纯文字语义色（`src/utils/uiClasses.ts`）。 |
| **列表工具栏单行** | `TOOLBAR_ROW_LIST`：`flex-nowrap` + `overflow-x-auto` + 隐藏滚动条（`src/utils/toolbarFieldClasses.ts`）。已用于公告、限流、模型配置、审计日志、授权申请、入驻审批、资源审核列表、健康检查工具栏等。 |
| **平台公告** | 关键词 + 类型下拉宽度已通过 `LantuSelect` 的 `className="!w-36 shrink-0"` 纠正「下拉占满剩余宽」问题（根因：`LantuSelect` 默认 `w-full`）。 |

### 1.2 仍与「胶囊纯文字行内操作」不一致的页面（控制台 / 审核台）

以下列表仍以 **图标按钮** 或 **图标+文字** 为主，若产品要求与管理配置页完全统一，需单独排期改造：

- `src/views/agent/AgentList.tsx` — 编辑/删除含 `Edit2` / `Trash2`。
- `src/views/agent/AgentAuditList.tsx` — 详情 `Eye`、通过/驳回 `CheckCircle2` / `XCircle`。
- `src/views/apps/AppList.tsx` — 编辑/删除图标及移动端菜单内图标。
- `src/views/dataset/DatasetList.tsx` — 同上。
- `src/views/skill/SkillList.tsx` — 查看/删除图标及菜单。
- `src/views/skill/SkillAuditList.tsx` — 同 Agent 审核台模式。

**说明**：原规划中将控制台资源列表标为「可选同一轮替换」；当前仓库状态与此一致。

### 1.3 工具栏仍使用 `flex-wrap` 的管理页（非必须改成单行）

以下页面工具栏或筛选区仍为 `flex-wrap`，多为字段较多或产品未要求强制单行；若需与公告页绝对一致，可再套 `TOOLBAR_ROW_LIST` 或局部 `flex-nowrap`：

- `UserListPage`、`ResourceGrantManagementPage`、`ProviderManagementPage`、`ResourceCenterManagementPage`（卡片行内操作区）、`TagManagementPage`、`TokenListPage`、`CallLogPage`、`UsageRecordsPage`、`AlertMgmtPage` 等。

### 1.4 组件层面的注意点（`LantuSelect`）

- 外壳默认为 `relative w-full`（`src/components/common/LantuSelect.tsx`）。在 **横向 flex 工具栏** 中若希望下拉不占满剩余空间，调用方需传 `className` 覆盖，例如 `!w-36 shrink-0` 或 `!w-[8rem] shrink-0`。
- 健康检查页已对齐：`HealthConfigPage` 使用 `TOOLBAR_ROW_LIST`，且 `LantuSelect` 使用固定宽度类，避免再次被 `w-full` 拉宽。

---

## 2. 后端接口与检索能力补充建议

下列场景在前端存在 **仅当前页过滤**、**全量在客户端过滤** 或与 **分页 total 语义不一致** 的风险，建议后端提供明确查询参数并在**服务端完成筛选与计数**，以保证分页与搜索结果一致。

### 2.1 平台公告 `GET /system-config/announcements`

**现状（前端）**

- `systemConfigService.listAnnouncements` 已声明可选参数 `keyword`、`type`（`src/api/services/system-config.service.ts`），但 **`AnnouncementPage` 拉列表时未传入**，仅用当前页的 `list` 做 `filterKeyword` / `filterType` 的内存过滤。
- 分页 `total` 为全库维度时，与「仅过滤当前页」的 UI 行为可能让用户困惑（代码注释已说明）。

**建议后端**

- 支持查询参数（与现有 service 类型对齐即可）：
  - `keyword`：匹配 `title`、`summary`（若合适也可含 `content` 片段，需约定性能与长度）。
  - `type`：与公告类型枚举一致（如 `notice` / `maintenance` 等，以前端 `TYPE_FILTER_OPTIONS` 为准）。
- 响应分页：`total` 表示 **过滤后的总条数**，与 `list` 同级语义一致。

**建议前端（后续）**：`fetchList` 将 `filterKeyword`、`filterType` 传入 `listAnnouncements`，并去掉或弱化纯前端 `filteredRows`（或仅作防抖兜底）。

---

### 2.2 授权申请审批 `GET /grant-applications/pending`

**现状（前端）**

- `grantApplicationService.listPending` 仅传 `status`、`page`、`pageSize`。
- 搜索框通过 `filteredRows` **仅在当前页** 上对 `id`、`resourceType`、`resourceId`、`apiKeyId`、`actions`、`useCase` 等拼接字符串做 `includes`。

**建议后端**

- 增加可选 `keyword`（或 `q`）：对上述字段及申请人相关字段做统一模糊/分词检索（具体字段与前表 `GrantApplicationVO` 对齐）。
- 保证分页 `total` 为过滤后总数。

---

### 2.3 开发者入驻申请 `GET /developer/applications`

**现状（前端）**

- `DeveloperApplicationQueryRequest` 已含 `keyword?: string`（`src/types/dto/developer-application.ts`）。
- `DeveloperApplicationListPage` 调用 `developerApplicationService.list({ page, pageSize })` **未传 `keyword`**，搜索完全在客户端对当前页 `list` 过滤（姓名、邮箱、单位等）。

**建议后端**

- 确认 `keyword` 已在服务端实现；覆盖：`userName` / `username` / `userId`、`contactEmail`、`companyName`、`applyReason` 等（按产品定义裁剪）。
- **建议前端（后续）**：将 `search` 防抖后作为 `keyword` 传入并重拉列表，删除或保留 `filteredList` 仅作兜底。

---

### 2.4 审计日志 `GET /system-config/audit-logs`

**现状（前端）**

- `listAuditLogs` 使用 `PaginationParams` + 业务里扩展了 `action`（见 `useSysAuditLogs`）。
- `AuditLogPage` 中 **关键词搜索**、**仅失败** 均在 **当前页返回的 `logs` 上** 用 `useMemo` 过滤；`exportCsv` 导出的是过滤后的当前页内存数据，而非全库筛选结果。

**建议后端**

- 扩展查询参数（示例）：
  - `keyword`：对 `operator`、`action`、`resource`、`ip` 等做 OR 模糊匹配（或指定字段）。
  - `result` 或 `onlyFailure: boolean`：仅失败记录。
  - （可选）`timeFrom` / `timeTo`：与列表时间列对齐，便于大表分页。
- 分页 `total` 为服务端过滤后的总数。

**建议前端（后续）**：将搜索框与「仅失败」同步为请求参数，导出 CSV 时或增加「导出当前筛选（服务端）」接口以避免仅导出当前页。

---

### 2.5 敏感词列表 `GET /sensitive-words`

**现状**

- Service 已支持 `category`、`enabled` 等（`sensitive-word.service.ts`）。列表以分页拉取为主。

**可选增强**

- 若管理端需「全文检索词面」，可增加 `keyword` 参数；前端表格列多时可减少全量拉取压力。

---

### 2.6 已具备服务端检索的模块（供对照）

以下模块列表已传 `keyword` 或等价参数至后端（无需在本节重复需求，除非后端尚未实现）：

- 提供商：`ProviderList` / `ProviderManagementPage` 的 `keyword`。
- 用户列表：`UserListPage` 的 `keyword`。
- 资源中心管理列表：`ResourceCenterManagementPage` 的 `keyword`。
- 统一资源审核台：`ResourceAuditList` 的 `keyword`。
- Agent / App / Dataset 管理端列表：各自 `list` 接口的 `keyword`。

---

## 3. 变更记录（文档维护）

| 日期 | 说明 |
|------|------|
| 2026-03-30 | 初版：UI 巡检结果 + 公告 / 授权申请 / 入驻 / 审计 等后端检索建议；健康检查工具栏与 `TOOLBAR_ROW_LIST`、`LantuSelect` 宽度对齐。 |

---

## 4. 小结

- **管理配置壳层与多数 Mgmt 列表工具栏**已按规划收敛；**控制台 Agent/App/Dataset/Skill 列表与双审核台**仍为图标行内操作，属已知范围差异。
- **公告、授权审批、入驻审批、审计日志**等处存在「服务端分页 + 客户端筛选」的语义缺口；后端补足 `keyword` / 类型 / 结果等参数后，前端可小改动对接，消除「搜不到全库」「total 与当前结果不符」类产品体验问题。

如需后端按接口路径出 OpenAPI 片段或字段级约定，可在本文件后续追加章节对接。
