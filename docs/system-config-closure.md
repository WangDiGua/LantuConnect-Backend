# 超管「系统配置」闭环说明

对照侧栏分组：**基础 / 策略 / 审计 / 内容治理**。五类统一资源为 **Agent、Skill、MCP、App、Dataset**；部分页为平台级能力，不强制按资源维度拆分，但在配额、限流、标签、敏感词、审计筛选等处已对齐。

## 页面对照表

| 侧栏项 | 前端页面 / 模块 | 主要 API | 后端 | 五类资源 |
|--------|-----------------|----------|------|----------|
| 标签管理 | `TagManagementPage` | `GET/POST/DELETE /tags` | `TagController` | **强**：分类即五类 + general |
| 系统参数 | `SystemParamsPage` | `GET/PUT /system-config/params` | `SystemParamController` | 弱：可在 JSON 覆盖中配置资源相关开关 |
| 安全设置 | `SecuritySettingsPage` | `GET/PUT /system-config/security` | 同上 | 平台安全 |
| 网络配置 | `NetworkConfigPage` | `GET /system-config/network/allowlist`、`POST /system-config/network/apply` | 同上 | 弱：五类资源的公网策略在网关其它管道 |
| 配额管理 | `QuotaManagementPage` | `GET/POST/PUT/DELETE /quotas`、`/rate-limits*` | `QuotaController`、`QuotaRateLimitController` | **强**：resourceCategory / targetType |
| 限流策略 | `RateLimitPage` | `GET/POST/PUT/DELETE /system-config/rate-limits` | `RateLimitRuleController` | **强**：resourceScope |
| 访问控制 | `AccessControlPage` | `GET /system-config/acl`、`POST /system-config/acl/publish` | `SystemParamFacadeService` 持久化 `api_path_acl_rules` | 路径级 RBAC；资源级见资源中心 |
| 审计日志 | `AuditLogPage` | `GET /system-config/audit-logs` | `SystemParamController` | **筛选项**：resourceType 五类 |
| 敏感词 | `SensitiveWordPage` | `/sensitive-words*` | `SensitiveWordController` | **强**：category 含五类 + general |
| 平台公告 | `AnnouncementPage` | `/system-config/announcements*` | `AnnouncementController` | 面向全体用户，不单绑资源类型 |

## 已实现的数据持久化（补齐项）

- **网络白名单**：`POST /system-config/network/apply` 请求体 `{ "rules": ["CIDR", ...] }` 写入 `t_system_param.key = admin_network_allowlist`（JSON 数组字符串）。
- **路径 ACL**：`POST /system-config/acl/publish` 请求体 `{ "rules": [{ "id", "path", "roles" }] }` 写入 `api_path_acl_rules`。
- **GET /system-config/acl**：`rules` 为上述路径规则；`roleCatalog` 为平台角色简表（`roleCode` / `roleName`），供管理端对照。

## 与两套「限流」的关系

- **`/system-config/rate-limits`**：网关策略维度（global/user/ip/path + 可选 resourceScope）。
- **`/rate-limits`（配额页内 tab）**：配额绑定型限流，与 `QuotaController` 体系一致。

两者互补，勿混为同一数据表。
