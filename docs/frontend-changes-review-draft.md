# 前端改动审查稿（与后端 2026-04 权限 / 策略 / 统计对齐）

> **目的**：把此前讨论过的「应用端 / 管理端」信息架构与导航体验，以及后端已落地（见 `docs/permission-flow-comparison-and-plan.md` 阶段 A–G、`PRODUCT_DEFINITION.md`、`docs/resource-registration-authorization-invocation-guide.md`）的能力，整理成**可评审的前端任务清单**。  
> **范围**：前端仓库默认路径为 `LantuConnect-Frontend`（与后端并列）；本文不实施代码，仅供你审查优先级与边界。  
> **更新时间**：2026-04-03

---

## 1. 背景：后端已就绪、前端待对齐的点

后端已完成但不限于：`t_resource.access_policy` 与网关 Grant 短路、`consumer` 系统角色、Grant 工单按 owner / 部门 / 平台过滤与审批权下放、审核发布权与平台强制下架、`GET /dashboard/owner-resource-stats`、技能包下载埋点等。前端若仍按「万事找平台管理员」「所有 Grant 仅超管批」等旧文案或旧菜单权限建模，会与真实接口行为不一致，并可能挡掉 **开发者作为 owner 审批本资源授权申请** 的路径。

---

## 2. 信息架构：「应用端 / 管理端」与统一导航

### 2.1 现状（代码侧要点）

- 控制台通过 `UserRoleContext.platformRoleToConsoleRole` 将 `platform_admin` / `dept_admin` 映射为壳层 `admin`，其余为 `user`；路由由 `consoleRoutes` + `MainLayout` 双域侧边栏（`ADMIN_SIDEBAR_ITEMS` / `USER_SIDEBAR_ITEMS`）驱动。
- `ConsoleHomeRedirect` 已按角色纠正「无权限却记住上次管理端路径」等情况（未赋权用户进入驻页等）。
- `MainLayout` 注释明确：**开发者中心**页面仅在**用户域**路由挂载；旧 `/admin/...` 链接触发重定向。

### 2.2 评审建议（产品 / UX）

| 主题 | 建议方向 |
|------|----------|
| 命名与心理模型 | 在顶栏或侧栏固定呈现「工作台」（用户域）与「管理后台」（admin 壳层）的差异时，避免与后端真实角色混用；用户文档已强调 `admin/user` 仅视图域（见 `docs/frontend-alignment-handbook.md` §2.8）。 |
| 减少重复入口 | 核对是否仍存在多处以不同文案跳转同一能力的入口（例如 API Key：个人设置 vs 用户管理），在文案上标明受众（当前 `ApiDocsPage` 已有部分说明，可延伸到其它页）。 |
| 角色切换 | 若产品仍提供显式「切换到管理端」控件，建议与 `canAccessAdminView` 一致，并对 **仅有 consumer / 普通 user** 的账号隐藏或禁用，避免 403 环路。 |

---

## 3. `consumer` 角色（只读逛市场）

### 3.1 后端语义

- 预置角色 `consumer`：`agent:read`、`skill:read`、`app:view`、`dataset:read`（`mcp` 与 `skill` 共用 `skill:read` 的目录策略与后端一致）。见 `PRODUCT_DEFINITION.md` §4、`resource-registration-authorization-invocation-guide.md` §3.3。

### 3.2 前端缺口（基于当前类型与上下文）

- `src/types/dto/auth.ts` 中 `PlatformRoleCode` **未包含** `consumer`；`normalizeRole` 对未知角色兜底为 `user` 并 `console.warn`。
- `ROLE_PERMISSIONS` 里 `user` 的只读集合与 `consumer` 接近，但 **产品语义不同**（consumer 不应假设具备「将来扩展为业务 user 的全集」）。

### 3.3 建议改动

1. 扩展 `PlatformRoleCode`，增加 `'consumer'`，并在 `ROLE_ALIAS` 中映射（若后端会返回别名一并列入）。  
2. 为 `consumer` 单独配置 `ROLE_PERMISSIONS`（与后端 JSON **逐字符串对齐**），`platformRoleToConsoleRole` 固定映射为 `user` 壳层、**`canAccessAdminView` 为 false**。  
3. 菜单可见性：`consumer` **不应**看到依赖 `agent:create` / `skill:create` / `developer:portal` 等的入口（`MainLayout` 内已有基于 `hasPermission` 的过滤逻辑，需逐项核对 `USER_SIDEBAR_ITEMS` 各组是否仍被「宽权限」误判）。  
4. 个人中心展示名：在 `UserProfile` 等处的角色中文映射中增加 `consumer`（例如「市场访客」/「消费者」，按产品定稿）。

---

## 4. 资源消费策略 `accessPolicy`

### 4.1 后端契约（摘要）

- 字段写入 `t_resource.access_policy`；枚举：`grant_required`（默认）、`open_org`、`open_platform`。  
- 创建可选；更新**未传则保留原值**。网关短路规则见 `PRODUCT_DEFINITION.md` §4 与实施文档 §3.1。

### 4.2 前端缺口

- `ResourceUpsertRequest` / `ResourceCenterItemVO`（`types/dto/resource-center.ts`）**未见** `accessPolicy`；注册表单 `ResourceRegisterPage` 未暴露该选项。  
- 市场 / 详情页未展示策略徽章时，调用方难以理解「为何仍要 Grant」或「为何同部门可免 Grant」。

### 4.3 建议改动

1. DTO：`ResourceBaseUpsertRequest` 或各类 upsert 的公共部分增加可选 `accessPolicy?: 'grant_required' | 'open_org' | 'open_platform'`（与后端枚举一致）；详情 VO 增加回显字段。  
2. 注册 / 编辑表单：在**拥有者可操作**的资源上提供三选一（配简短说明与风险提示）；提交时拼进 `PUT/POST` body。  
3. 只读展示：目录卡片或详情页显示策略标签；可与 `isPublic` 等字段并列，避免混淆（策略管 **Grant 短路**，不等同于「公开匿名」）。  
4. 联调：用不同 Key（用户 Key / 部门不一致）测 `open_org`；用 scope 齐全 Key 测 `open_platform`。

---

## 5. Grant 授权申请：权限、菜单与文案

### 5.1 后端行为（摘要）

- `GET /grant-applications/pending`：按角色过滤待办（超管全量、dept_admin 本部 owner 资源、普通登录用户仅自己名下资源上的申请）。  
- `approve` / `reject`：**不限定**仅 `platform_admin`，与 `ResourceInvokeGrantService` 的审批评审权一致。  
- 提交通知：**owner + 平台管理员**（实施文档 §3.2.1）。

### 5.2 前端缺口

| 项 | 说明 |
|----|------|
| **菜单权限** | `MainLayout` 中 `grant-applications` 子菜单绑定 `resource-grant:manage`（`SUB_ITEM_PERM_MAP`）。`developer` 的 `ROLE_PERMISSIONS` **不包含**该权限 → **多数开发者无法从侧栏进入「授权申请审批」页**，与后端「owner 可审本资源申请」矛盾。 |
| **用户域入口** | 「我的授权申请」仅覆盖**申请人**视角；**审批人（owner）** 缺少对称入口时，依赖分享链接或手输 URL。 |
| **文案** | `GrantApplicationModal` 等仍写「等待管理员审批」；应改为「等待资源拥有者 / 部门或平台管理员审批」之类可配置表述。 |
| **接入文档** | `ApiDocsPage` 仍可强调工单路由已下放，避免读者以为仅超管可操作。 |

### 5.3 建议改动

1. 为「审批待办」单独定义权限位（例如 `grant-application:review`）并在后端 Casbin **与 developer / dept_admin / platform_admin 对齐**；或前端对 `grant-applications` 使用 **OR** 条件：`resource-grant:manage` **或**（`developer` 且具备 owner 审批评审权 —— 后者若无法静态表达，则简化为：**developer 也显示该菜单项**，由列表接口返回空数据 + 服务器 403 兜底）。  
2. 产品优选：在用户域「个人资产」或「我的发布」下增加 **「待我审批的授权申请」**，与管理员台共用 `GrantApplicationListPage` 或抽取共享列表组件。  
3. 全文案：搜索结果替换「仅管理员」叙事，与 `resource-registration-authorization-invocation-guide.md` 一致。

---

## 6. 审核、发布与平台强制下架

### 6.1 后端

- `publish`：owner / 同部门 dept_admin / platform_admin、admin；仍仅 `testing → published`。  
- `POST /audit/resources/{id}/platform-force-deprecate`：**仅 platform_admin**。

### 6.2 前端建议

1. 审核列表 / 详情：发布按钮显隐与 `allowedActions` 或单独 `GET` 结果对齐，避免「后端已放开 developer 发布、前端仍藏按钮」。  
2. 平台强制下架：仅管理端展示危险操作；二次确认 + `reason` 与后端 body 一致。  
3. 文案区分：开发者**自助下线**（`deprecate`）vs **平台强制下架**（审计记录不同，见后端 `AuditLog`）。

---

## 7. Owner 维度统计 API

### 7.1 后端

- `GET /dashboard/owner-resource-stats?periodDays=&ownerUserId=`：聚合 call_log、usage_record(invoke)、skill_pack_download_event；权限：本人、同 `menu_id` 的 dept_admin、platform_admin/admin。

### 7.2 前端缺口

- 现有 `DeveloperStatsPage` 使用 `developerStatsService.getMyStatistics()`，**未必**对接上述新 API（需在 `developer-stats.service` / DTO 中核对路径与字段）。

### 7.3 建议改动

1. 新增或使用统一 service 方法调用 `owner-resource-stats`，类型与图表字段与后端响应一致。  
2. 页面策略：`dept_admin` 可查下拉选择本部门 owner（若产品需要）；`platform_admin` 可查任意 owner。  
3. 与 `PRODUCT_DEFINITION.md` §5 一致，在页内工具提示中说明：**调用量 ≠ 全部资产使用量**；技能下载单独统计。

---

## 8. 其它一致性 / 低耦合项

| 项 | 说明 |
|----|------|
| **工作台快捷入口** | `UserWorkspaceOverview` / `QuickAccess` 中指向 `resource-grant-management` 且依赖 `user:manage` 的卡片，与 **普通开发者** 路径不一致，易误导；建议按角色拆卡片或改权限 predicate。 |
| **类型与 Handbook** | `docs/frontend-alignment-handbook.md` §2.8 可增加 `consumer` 与 `accessPolicy`、统计 API 的索引，避免手册与代码长期脱节。 |
| **Swagger 暴露** | 后端默认 `expose-api-docs` 为 false；前端若在非 dev 环境拼接 swagger 链接，需环境判断。 |

---

## 9. 建议实施顺序（供排期）

1. **P0**：Grant 审批菜单 / 权限与文案（否则 owner 审批流在线上不可用）；`consumer` 角色类型与菜单。  
2. **P1**：`accessPolicy` DTO + 注册编辑 + 详情展示；审核发布按钮与强制下架。  
3. **P2**：`owner-resource-stats` 全量接入与图表；导航与快捷入口体验收口。  
4. **持续**：Handbook / ApiDocs / 市场页帮助文案与后端文档同步。

---

## 10. 修订记录

| 日期 | 说明 |
|------|------|
| 2026-04-03 | 初稿：对齐后端阶段 A–G 与 `LantuConnect-Frontend` 静态代码阅读结论；待产品确认菜单与权限建模最终方案。 |

---

*审查通过后，可将本节拆成前端仓库 issue；接口与枚举以 Swagger 及后端 DTO 为准。*
