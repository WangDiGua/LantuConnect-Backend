# 下拉框与枚举对齐总表（全站扫描 · 前端 → 后端）

**目的**：凡前端以「下拉 / 单选」形式写死的 **value**，后端应在 OpenAPI、字典表或校验规则中与之一致（大小写、拼写、取值集合）；凡标记为 **「后端补充」** 的，请提供权威枚举或字典接口，前端再收敛重复字面量。

**符号**：`[DTO]` 来自 `src/types/dto`；`[UI]` 页面硬编码；`[API]` 选项由接口动态返回；`[仅前端]` 仅用于 UI 过滤、不要求写入业务表。

---

## 1. 核心契约（优先与 DTO 对齐）

以下 **value 字符串** 出现在 API 请求/响应类型中，后端应以同一集合校验与文档化。

### 1.1 资源与目录

| 字段语义 | 允许 value（与前端类型一致） | 定义位置 |
|----------|------------------------------|----------|
| 资源类型 `resourceType` | `agent`、`skill`、`mcp`、`app`、`dataset` | `catalog.ts` → `ResourceType` |
| 资源审核状态 | `pending_review`、`testing`、`published`、`rejected`（及列表筛选用 `all` 为前端占位） | `audit.ts`、`resource-center` 相关 VO |
| 授权动作 `actions` | `catalog`、`resolve`、`invoke`、`*` | `catalog.ts` |
| 目录排序 | `sortOrder`: `asc` \| `desc`；`sortBy` 见各 Query | `catalog.ts` 等 |

### 1.2 Agent / Skill（旧 DTO，控制台仍引用）

| 字段 | 允许 value | 定义位置 |
|------|------------|----------|
| `agentType` | `mcp`、`http_api`、`builtin` | `agent.ts` |
| `sourceType` | `internal`、`partner`、`cloud` | `agent.ts` |
| `AgentStatus` | `draft`、`pending_review`、`testing`、`published`、`rejected`、`deprecated` | `agent.ts` |
| `mode`（Skill） | `TOOL`（Skill）；`SUBAGENT` \| `ALL` 等见 Agent | `skill.ts` / `agent.ts` |
| `DisplayTemplate` | `file`、`image`、`audio`、`video`、`app`、`microService`、`search_web`、`search_file`、`answer`、`ai_answer` | `agent.ts` |

**后端比对**：若网关或新资源中心使用不同别名（如 `http-api`），需在 BFF 映射或统一文档中声明。

### 1.3 应用 SmartApp

| 字段 | 允许 value | 定义位置 |
|------|------------|----------|
| `embedType` | `iframe`、`micro_frontend`、`redirect` | `smart-app.ts` |
| `AppStatus` | `draft`、`published`、`testing`、`deprecated` | `smart-app.ts` |
| `sourceType`（展示） | `internal`、`partner` 等 | `smart-app.ts` |

### 1.4 数据集 Dataset

| 字段 | 允许 value | 定义位置 |
|------|------------|----------|
| `DatasetSourceType` | `department`、`knowledge`、`third_party` | `dataset.ts` |
| `DatasetDataType` | `document`、`structured`、`image`、`audio`、`video`、`mixed` | `dataset.ts` |
| `DatasetStatus` | `draft`、`published`、`testing`、`deprecated` | `dataset.ts` |

### 1.5 提供商 Provider

| RelevantCode | 允许 value | 定义位置 |
|--------------|------------|----------|
| `ProviderType` | `internal`、`partner`、`cloud` | `provider.ts` |
| `AuthType` | `api_key`、`oauth2`、`basic`、`none` | `provider.ts` |
| `ProviderStatus` | `active`、`inactive` | `provider.ts` |

### 1.6 用户与权限（user-mgmt / auth）

| 字段 | 允许 value | 定义位置 |
|------|------------|----------|
| 用户状态 | `active`、`disabled`、`locked` | `user-mgmt.ts`、`auth.ts` |
| Token/API Key 状态 | `active`、`expired`、`revoked` | `user-mgmt.ts`、`user-settings.ts` |
| 平台角色（示例） | `platform_admin`、`dept_admin`、`developer`、`user` | `user.ts` `RoleCode`；`auth.ts` 含 `unassigned` |
| 组织节点类型（若用） | `company`、`department`、`team`、`group` | `user-mgmt.ts` |

**角色权限勾选**：`src/constants/userMgmt.ts` 中 `PERMISSION_PRESETS` 的 `id`（`user`、`role`、`agent`、`api`、`audit`）为 **UI 分组 id**，写入权限时形如 `agent:*`；后端需与权限模型一致。

### 1.7 监控与告警

| 字段 | 允许 value | 定义位置 |
|------|------------|----------|
| 调用日志 `status` | `success`、`error`、`timeout` | `monitoring.ts`、`CallLogPage` 筛选 |
| 告警记录 `severity` | `critical`、`warning`、`info` | `monitoring.ts` |
| 告警记录 `status` | `firing`、`resolved`、`silenced` | `monitoring.ts` |
| 告警规则 `operator` / `condition` | `gt`、`lt`、`eq`（表单）；DTO 尚含 `gte`、`lte` | `monitoring.ts`、`AlertRulesPage` |
| KPI `changeType` | `up`、`down`、`flat` | `monitoring.ts` |

**告警规则指标**：前端表单选项含 `http_5xx_rate`、`latency_p99`、`error_rate`；若后端支持更多 metric，请 **返回字典** 或 OpenAPI enum，前端再扩展 `METRIC_OPTIONS`。

**通知渠道**：前端多选 `ding`、`email`、`webhook`；后端需确认是否同一套 id。

### 1.8 健康与熔断

| 字段 | 允许 value | 定义位置 |
|------|------------|----------|
| `checkType` | `http`、`tcp`、`ping` | `health.ts` |
| `healthStatus` | `healthy`、`degraded`、`down` | `health.ts` |
| 熔断状态（业务态） | `CLOSED`、`OPEN`、`HALF_OPEN` | `CircuitBreakerPage` 客户端筛选与展示 |

### 1.9 配额 Quota

| 字段 | 允许 value | 定义位置 |
|------|------------|----------|
| 配额 `targetType` | `global`、`department`、`user` | `quota.ts` |
| 限流规则 `targetType` | `global`、`agent`、`skill` | `quota.ts` |

### 1.10 申请类

| 类型 | 允许 value | 定义位置 |
|------|------------|----------|
| 授权申请 `status` | `pending`、`approved`、`rejected` | `grant-application.ts` |
| 开发者入驻 `status` | `pending`、`approved`、`rejected`、`unknown` | `developer-application.ts` |

### 1.11 公告与探索

| 字段 | 允许 value | 定义位置 |
|------|------------|----------|
| 公告 `type` | `feature`、`maintenance`、`update`、`notice` | `explore.ts`、`AnnouncementPage` |

### 1.12 审计与系统配置

| 字段 | 允许 value | 定义位置 |
|------|------------|----------|
| 审计日志 `result`（query） | `success`、`failure` | `system-config.service` `AuditLogQueryParams` |
| 限流策略 `target`（DTO） | `user`、`role`、`ip`、`api_key`、`global` | `system-config.ts` |
| 限流 `action` | `reject`、`queue`、`throttle` | `system-config.ts` |

---

## 2. 页面硬编码筛选（[UI]）：后端需与同表 1 一致

以下为 **FilterSelect / LantuSelect** 中与业务相关的 value；若与 DTO 不一致，以 **DTO + 真实落库值** 为准并推动前端改字面值。

| 页面 / 模块 | 选项含义 | 典型 value（节选） |
|-------------|----------|---------------------|
| `AnnouncementPage` | 公告类型 | `feature`、`maintenance`、`update`、`notice` |
| `AuditLogPage` | 操作类型 | `全部`、`CREATE`、`UPDATE`、`DELETE`、`LOGIN`、`LOGOUT`、`EXPORT`、`DEPLOY`…（**后端补充**：完整 action 字典） |
| `SensitiveWordPage` | 启用筛选 | `''`、`true`、`false`（字符串传给 query） |
| `GrantApplicationListPage` | 状态 | `all`、`pending`、`approved`、`rejected` |
| `DeveloperApplicationListPage` | 同逻辑 | 与入驻状态一致 |
| `UserListPage` | 用户状态筛选 | `all`、`active`、`disabled` |
| `TokenListPage` | Token 状态 | `all`、`active`、`revoked`、`expired` |
| `CallLogPage` | 调用状态 | `all`、`success`、`error`、`timeout` |
| `AlertMgmtPage` | 级别 / 告警状态 | `critical`、`warning`、`info`；`firing`、`resolved`、`silenced` |
| `AlertRulesPage` | 规则级别筛选 + 表单 | 同上 + `METRIC_OPTIONS`、`OPERATOR_OPTIONS`、`CHANNEL_OPTIONS` |
| `ResourceAuditList` | 资源类型 / 状态 | 见 §1.1 + `pending_review`、`testing`、`rejected`、`published` |
| `ResourceCenterManagementPage` | 资源类型等 | 与 `ResourceType` 一致 |
| `ProviderList` / `ProviderManagementPage` | 提供商类型 / 状态 | `internal`/`partner`/`cloud`；`active`/`inactive` |
| `ProviderManagementPage` | `authType` | `none`、`api_key`、`oauth2`、`basic` |
| `AgentList` / `AppList` / `DatasetList` / `SkillList` | 状态 / 来源 / 类型 | filter 集合应与 **§1.2–1.4** 一致（注意 `_` 与拼写） |
| `RateLimitPage` | 目标类型 / 动作 | `RATE_LIMIT_TARGET_OPTIONS`、`RATE_LIMIT_ACTION_OPTIONS`（见页面常量） |
| `ModelConfigPage` | 模型提供方 | `MODEL_PROVIDER_OPTIONS`（前端品牌枚举，**后端可对齐或改由配置下发**） |
| `QuotaManagementPage` | 配额范围 / 限流目标 | 与 `quota.ts` 一致 |
| `CircuitBreakerPage` | 状态筛选 | `all`、`CLOSED`、`OPEN`、`HALF_OPEN`（[仅前端] 内存过滤也可接受） |
| `HealthConfigPage` | 健康状态筛选 + 检查方式 | `healthy`、`degraded`、`down`；`http`、`tcp`、`ping` |
| `ResourceGrantManagementPage` | 资源类型 | `agent`、`skill`、`mcp`、`app`、`dataset` |
| `OrgStructurePage` / `CategoryManagement` | 父节点 | `[API]` 自列表/树构造，非固定枚举 |
| `TagManagementPage` | 标签分类 | `Agent`、`Skill`、`MCP`、`应用`、`数据集`、`通用`（**产品语义**；若入库为字符串，后端需统一） |
| `ApiPlaygroundPage` | HTTP 方法 | `GET`、`POST`、`PUT`、`DELETE` |
| `AgentMarket` | 排序 | `MARKET_SORT_OPTIONS` 内 value（[仅前端] 或对接 `sortBy`） |
| `SkillCreate` | `SKILL_CATEGORY_OPTIONS` 等 | 部分为产品占位，**建议改分类服务 API** |
| `ResourceRegisterPage` | 多组下拉 | 资源类型、标签、连接方式等混合 **[API]** 与 **[UI]** |

---

## 3. 建议后端「补充字典」的缺口（前端未列全或仅示例）

| 场景 | 说明 |
|------|------|
| 审计日志 `action` | 前端为示例列表；请提供完整枚举或 `GET /system-config/audit-actions` |
| 告警规则 `metric` | 前端 3 项示例；请 OpenAPI enum 或字典接口 |
| 模型提供商 / 厂商 | `ModelConfigPage` 可能滞后于实际接入；建议配置化 |
| 标签业务分类 | `TAG_CATEGORIES` 与 DB 是否一致需产品+后端确认 |

---

## 4. 动态下拉（[API]，不是固定枚举）

下列选项**来自接口或列表推导**，后端应保证列表字段稳定，而非要求前端写死全集：

| 数据 | 来源示例 |
|------|-----------|
| 分类树 / 父节点 | `categoryService.list`、`OrgStructurePage` |
| 敏感词分类 | `GET /sensitive-words/categories` |
| 平台角色（用户表单） | `userMgmtService.listRoles()` |
| 标签列表 | `tagService.list()` |
| MCP 父节点 / Skill 父 Agent | `skillService` / 目录 API |
| 资源注册页 tags | 标签 API |

---

## 5. 与 .cursor 数据模型规则的关系

仓库内 `data-models.mdc` 中的 `User`、`OrgNode`、`PlatformRole`、`Agent`、`Skill`、`SmartApp`、`Dataset`、`Provider`、`Category` 等 **字段名与枚举** 为产品级约束；若本表与规则冲突，**以 DTO 源码与实际 API 为准**后应同步更新规则文档。

---

## 6. 变更记录

| 日期 | 说明 |
|------|------|
| 2026-03-30 | 初版：按 DTO + grep `LantuSelect`/`FilterSelect`/`_OPTIONS` 汇总；标注 API 动态项与待字典项。 |
