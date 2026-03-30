# 后端核对用：枚举值与字典接口契约

本文档供 **LantuConnect 后端** 在实现校验、OpenAPI、筛选参数时与前端对齐使用。内容源自并扩展 [02-dropdown-enums-alignment.md](./02-dropdown-enums-alignment.md)：在保留前端 DTO 字面量的同时，补充 **本仓库已提供的 HTTP 字典类接口** 与 **约定别名**。

**原则**：落库值、query 字符串、JSON 字段应与下表 **大小写及拼写一致**；若网关使用别名（如 `http-api`），必须在 BFF 或文档中显式映射。

---

## 1. 资源与审核

| 语义 | 允许值 |
|------|--------|
| `resourceType` | `agent`, `skill`, `mcp`, `app`, `dataset` |
| 资源审核状态 | `pending_review`, `testing`, `published`, `rejected`；列表可用 `all` |
| 授权 `actions` | `catalog`, `resolve`, `invoke`, `*` |

---

## 2. Agent / Skill / App / Dataset / Provider

| 语义 | 允许值（节选） |
|------|----------------|
| `agentType` | `mcp`, `http_api`, `builtin` |
| `sourceType` | `internal`, `partner`, `cloud` |
| Agent 等资源状态 | `draft`, `pending_review`, `testing`, `published`, `rejected`, `deprecated`（以各资源为准） |
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

## 维护

| 日期 | 说明 |
|------|------|
| 2026-03-30 | 初版：浓缩 02 + 本仓字典接口 + `/rate-limits` keyword、告警 `status` 别名、ACL 来源说明。 |

详见前端总表：[02-dropdown-enums-alignment.md](./02-dropdown-enums-alignment.md)。Handoff 索引：[README.md](./README.md)。
