# 后端核对用：枚举值与字典接口契约

本文档供 **NexusAI Connect 后端** 在实现校验、OpenAPI、筛选参数时与前端对齐使用。内容源自并扩展 [02-dropdown-enums-alignment.md](./02-dropdown-enums-alignment.md)：在保留前端 DTO 字面量的同时，补充 **本仓库已提供的 HTTP 字典类接口** 与 **约定别名**。

**原则**：落库值、query 字符串、JSON 字段应与下表 **大小写及拼写一致**；若网关使用别名（如 `http-api`），必须在 BFF 或文档中显式映射。

---

## 1. 资源与审核

| 语义 | 允许值 |
|------|--------|
| `resourceType` | `agent`, `skill`, `mcp`, `app`, `dataset` |
| 资源审核状态 | `pending_review`, `published`, `rejected`；列表可用 `all` |
| 授权 `actions` | `catalog`, `resolve`, `invoke`, `*` |

**`skill` 与 `mcp`（硬语义）**

- **`skill`**：**Context 技能**（`executionMode=context`、`skillType=context_v1`、`contextPrompt`、可选 `parametersSchema` / `relatedMcpResourceIds`）。**不支持**统一网关 `POST /invoke` / `invoke-stream`；`resolve` 返回 `invokeType=portal_context`、`contextPrompt`、`parametersSchema` 与绑定 MCP。
- **`mcp`**：**可远程调用的 MCP/HTTP 工具**（`t_resource_mcp_ext`：`endpoint`、`protocol`、`authConfig`）。目录 `type=mcp` **仅** `resource_type='mcp'`，不再与前述 `skill` 混排。
- 禁止将 `skillType` 注册为 `mcp` / `http_api`；此类能力必须新建 `resourceType=mcp` 资源。

**Skill 包 zip（历史归档，当前实现不适用）**

> 以下表格用于解释历史迁移字段，不应再作为当前 Skill 契约使用；当前真值以上一节的 Context Skill 定义为准。

| 语义 | 说明 |
|------|------|
| `pack_validation_status`（`t_resource_skill_ext`） | `none`、`pending`、`valid`、`invalid`；资源详情/VO 字段 `packValidationStatus`、`packValidatedAt`、`packValidationMessage` |
| 提审前置 | `POST /resource-center/resources/{id}/submit`：技能类型须 **`pack_validation_status=valid`** 且 `artifact_uri` 非空 |
| 草稿入参 | 注册 `PUT/POST` 时 `artifactUri` 可为空；补齐方式：专用上传接口（见下） |
| 上传 | `POST /api/resource-center/resources/skills/package-upload`，`multipart/form-data`：`file`（zip）；可选 `resourceId`（已有 skill 则换代上传并重建当前版本快照） |
| URL 导入 | `POST /api/resource-center/resources/skills/package-import-url`，`application/json`：`{ "url": "https://…/xxx.zip", "resourceId"?: number }`；服务端拉取 zip，新建时 `sourceType=cloud`；配置见 `lantu.skill-pack-import`（`https-only`、`max-redirects`、`allowed-host-suffixes`、`require-allowed-host-suffixes`、大小与超时） |
| URL 导入安全说明 | 校验阶段会对主机做 DNS 解析与内网/保留地址拦截；**真实建连仍会再次解析 DNS**，存在 DNS 重绑定类 SSRF 理论风险。生产建议：`require-allowed-host-suffixes=true` 且配置可信制品域名后缀；或接受仅在内网 DNS 可信时使用。写入资源的描述字段仅保留 **scheme + host + port + path**，**不包含** query / fragment，避免预签名等凭证落库。HTTP 下载在**短事务外**执行，入库与校验 zip 在事务内完成。 |
| `resolve` 私有制品 | `isPublic=false` 时响应 **不** 返回直链 `endpoint`；`spec.artifactDownloadApi` = `/api/resource-center/resources/{resourceId}/skill-artifact` |
| 制品下载 | `GET /api/resource-center/resources/{id}/skill-artifact`：需登录；**已发布且非公开** 时需 `X-Api-Key` + 资源 `resolve` 授权（与网关解析一致）；拥有者/部门或平台管理员任意状态可下载本资源制品 |

---

## 2. Agent / Skill / App / Dataset / Provider

| 语义 | 允许值（节选） |
|------|----------------|
| `agentType` | `mcp`, `http_api`, `builtin` |
| Skill 类型 `skillType`（仅 `resourceType=skill`） | `context_v1` |
| `sourceType` | `internal`, `partner`, `cloud` |
| Agent 等资源状态 | `draft`, `pending_review`, `published`, `rejected`, `deprecated`（以各资源为准） |
| Provider `providerType` | `internal`, `partner`, `cloud` |
| Provider `authType` | `api_key`, `oauth2`, `basic`, `none` |
| Provider `status` | `active`, `inactive` |

---

## 3. 用户、Token、角色

| 语义 | 允许值 |
|------|--------|
| 用户 `status` | `active`, `disabled`, `locked` |
| API Key / Token `status` | `active`, `expired`, `revoked` |
| 平台角色 `roleCode`（示例） | `platform_admin`, `dept_admin`, `developer`, `user`, `admin` |

**权限字符串**：与 Casbin 一致，多为 `资源:操作`，如 `agent:read`、`monitor:view`。

---

## 4. 监控与告警

| 语义 | 允许值 |
|------|--------|
| 调用日志 `status` | `success`, `error`, `timeout`；筛选占位 `all` |
| 告警 `severity` | `critical`, `warning`, `info` |
| 告警记录 `status` | `firing`, `resolved`, `silenced`；筛选占位 `all` |

**Query 别名**：`GET /monitoring/alerts` 使用 `PageQuery`。优先使用 `alertStatus`；若未传 `alertStatus` 且 `status` 为 `firing`/`resolved`/`silenced` 之一，则按告警状态过滤（与仅传 `alertStatus` 等价）。调用日志仍使用 `status` 表示 HTTP 调用结果，勿混用。

**告警规则指标 id**（与前端 METRIC_OPTIONS 对齐）：`http_5xx_rate`, `latency_p99`, `error_rate` — 接口 `GET /monitoring/alert-rule-metrics`。

**告警规则创建/更新请求体**（`POST /monitoring/alert-rules`、`PUT /monitoring/alert-rules/{id}`，`AlertRuleCreateRequest` / `AlertRuleUpdateRequest`）：

| 字段 | 说明 |
|------|------|
| `operator` | 可选。比较算子，与 Dry-Run 一致：`gt` / `gte` / `lt` / `lte` / `eq`；亦接受 `>`、`>=` 等符号，服务端规范化为小写英文。省略时默认 `gte`。 |
| `severity` | 可选。`critical` / `warning` / `info`；历史别名 `medium` 会映射为 `warning`。省略时默认 `warning`。 |
| `duration` | 可选。持续时间窗口，如 `5m`、`1h`。省略时默认 `5m`。 |
| `conditionExpr` | 可选。仍写入实体 `description` 列（说明文案），与算子字段独立。 |

**调用日志 `resource_type`（网关写入）**：`t_call_log.resource_type` 为网关 `POST /invoke`、`POST /invoke-stream` 写入的目标资源类型（小写，如 `agent`、`mcp`、`app`）。当前 `skill` 为 Context-only，不应再视作新的统一调用目标；历史数据可能为 `NULL`。

**质量历史** `GET /monitoring/resources/{type}/{id}/quality-history`：按 `agent_id` = 资源主键字符串、且 `resource_type` 与路径 `{type}`（小写）一致聚合；**兼容**：`resource_type IS NULL` 的旧行仅在路径 `{type}` 为 `agent` 时纳入（与历史仅按 `agent_id` 统计的行为一致）。

---

## 5. 申请、公告、审计

| 语义 | 允许值 |
|------|--------|
| 授权申请 `status` | `pending`, `approved`, `rejected`；列表可用 `all` |
| 开发者申请 `status` | `pending`, `approved`, `rejected`, `unknown` |
| 公告 `type` | `feature`, `maintenance`, `update`, `notice` |
| 审计日志 query `result` | `success`, `failure`（与 `onlyFailure=true` 并存时以 `result` 为准） |

**审计 `action` 字典**：`GET /system-config/audit-actions`（`t_audit_log` 中去重，最多 500 条）。

---

## 6. 限流策略（system-config）

| 语义 | 允许值 |
|------|--------|
| DTO `target` | `user`, `role`, `ip`, `api_key`, `global` |
| `action` | `reject`, `queue`, `throttle` |

---

## 7. 健康与熔断（展示层）

| 语义 | 允许值 |
|------|--------|
| `checkType` | `http`, `tcp`, `ping` |
| `healthStatus` | `healthy`, `degraded`, `down` |
| 熔断（多仅为 UI） | `CLOSED`, `OPEN`, `HALF_OPEN` |

### 资源标签与目录筛选

| 概念 | 说明 |
|------|------|
| 标签字典 | `GET /tags`（`t_tag`）；写接口需 `platform_admin`。 |
| 注册 `categoryId` | `ResourceUpsertRequest.categoryId` = `t_tag.id`，写入 `t_resource.category_id`，并在 **create/update** 时同步 **`t_resource_tag_rel`**（`resource_type` + `resource_id` + `tag_id`）。**delete（软删）** 时删除该资源在 `t_resource_tag_rel` 中的行。 |
| 数据集 `tags` | `t_resource_dataset_ext.tags` JSON 数组（自由文案）；仅当字符串与 **`t_tag.name` 精确匹配且唯一** 时额外写入 `t_resource_tag_rel`；不自动新建 `t_tag`。 |
| 目录筛选 `tags` | `GET /catalog/resources` 的 query `tags`：按 **标签名** 过滤，依赖 `t_resource_tag_rel` JOIN `t_tag`。 |
| 目录列表 `tags` | `ResourceCatalogItemVO.tags`：当前资源在 rel 表中的标签 **name** 列表（批量查询）。 |
| 管理端 `ResourceManageVO` | `catalogTagNames`：同上；dataset 的 `tags` 仍为扩展表 JSON。 |

---

## 8. 动态下拉 / 字典接口（本仓库）

| 数据 | HTTP |
|------|------|
| 审计 action | `GET /system-config/audit-actions` |
| 告警规则 metric id | `GET /monitoring/alert-rule-metrics` |
| ACL 策略视图（平台角色 + permissions） | `GET /system-config/acl` → `rules[]`，`source=t_platform_role` |
| 敏感词分类 | `GET /sensitive-words/categories` |
| 平台角色列表 | `GET /user-mgmt/roles` |

---

## 9. 列表 keyword 与路径备忘

| 能力 | 路径或说明 |
|------|------------|
| 配额子表限流 | `GET /rate-limits?keyword=`（`name`/`targetName`/`targetType`/`targetId`） |
| 限流策略（全站规则） | `GET /system-config/rate-limits?keyword=` 或 `name=` |
| 提供商列表 | `GET /providers` 或 `GET /dataset/providers` |
| 用户列表 | `GET /user-mgmt/users`，`UserQueryRequest.keyword` / `status` |

---

## 10. 后端自检清单（发布前）

- [ ] 新增接口字段与上表枚举一致或已在 OpenAPI 标注 `enum`
- [ ] 筛选参数 `all`、空字符串语义与前端约定一致
- [ ] 与 `02-dropdown-enums-alignment.md` 冲突时：以**实际落库与线上前端**为准，并回写 02 或本表
- [ ] 字典类接口为空时（如新库无 audit 记录）：前端需兜底占位

---

## 11. 功能闭环增强（2026-03-31）

### 资源中心（生命周期）

| 能力 | 路径 / 字段 |
|------|-------------|
| Mine 列表后端筛选 | `GET /resource-center/resources/mine?resourceType=&status=&keyword=&sortBy=&sortOrder=` |
| Mine 列表动作建议 | `ResourceManageVO.allowedActions[]`（`update/submit/withdraw/deprecate/delete/createVersion/switchVersion`） |
| Mine 列表审核上下文 | `pendingAuditItemId`、`lastAuditStatus`、`lastRejectReason`、`lastSubmitTime`、`lastReviewTime`、`statusHint` |
| 生命周期时间线 | `GET /resource-center/resources/{id}/lifecycle-timeline` |
| 动作返回快照 | `POST /{id}/submit`、`POST /{id}/withdraw`、`POST /{id}/deprecate`、`POST /{id}/versions/{version}/switch` 统一返回 `ResourceManageVO` |

### 资源观测（统一摘要）

| 能力 | 路径 / 字段 |
|------|-------------|
| 统一观测摘要 | `GET /resource-center/resources/{type}/{id}/observability-summary` |
| 摘要结构 | `healthStatus`、`circuitState`、`qualityScore`、`qualityFactors`、`degradationHint` |
| 降级字典 | `CIRCUIT_OPEN`、`HEALTH_DEGRADED` |

### 目录 / 详情 include 扩展

| 能力 | 路径 / 字段 |
|------|-------------|
| 目录 include | `GET /catalog/resources?include=observability,quality,tags` |
| 详情 include | `GET /catalog/resources/{type}/{id}?include=observability,quality,tags` |
| resolve include | `POST /catalog/resolve` 请求体新增 `include` |
| SDK include | `GET /sdk/v1/resources/{type}/{id}?include=...` |

### 监控趋势与质量历史

| 能力 | 路径 / 字段 |
|------|-------------|
| KPI 趋势字段 | `GET /monitoring/kpis` 返回 `previousValue`、`changePercent`、`changeType` |
| 质量历史 | `GET /monitoring/resources/{type}/{id}/quality-history?from=yyyy-MM-dd HH:mm:ss&to=...`（按 `t_call_log.resource_type` + `agent_id` 过滤；见 §4） |
| dashboard 健康摘要增强 | `GET /dashboard/health-summary` 新增 `checks`、`statusDistribution`、`degradedResources` |

---

## 维护

| 日期 | 说明 |
|------|------|
| 2026-04-02 | 告警规则 `operator`/`severity`/`duration` 落库与 Dry-Run 一致；`t_call_log.resource_type` 与质量历史按类型过滤。 |
| 2026-03-31 ux-closure | 生命周期时间线、观测摘要、mine 列表筛选+动作建议、catalog/详情 include、monitoring KPI 趋势与 quality-history。 |
|2026-03-31 tags | 注册同步 `t_resource_tag_rel`；`GET /catalog/resources` 列表返回 `tags`；`ResourceManageVO.catalogTagNames`。 |
| 2026-03-31 | Skill 包：`pack_validation_*`、上传接口、提审门禁、`resolve`/`artifactDownloadApi`、受控下载 GET。 |
| 2026-03-30 | 初版：浓缩 02 + 本仓字典接口 + `/rate-limits` keyword、告警 `status` 别名、ACL 来源说明。 |

详见前端总表：[02-dropdown-enums-alignment.md](./02-dropdown-enums-alignment.md)。Handoff 索引：[README.md](./README.md)。
