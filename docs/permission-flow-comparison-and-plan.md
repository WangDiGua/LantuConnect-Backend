# 权限流程：现状 vs 目标 & 修改计划

本文档基于当前仓库代码与既有讨论整理，用于评审「开发者自治 + 部门专精 + 超管平台化」前的对齐。**接口路径与错误码以运行时代码为准**；实施后应同步 `PRODUCT_DEFINITION.md` 与前端手册。

> **2026-04-09 真值变更（须优先阅读）**：迁移 `sql/migrations/20260409_remove_resource_grants_and_open_catalog.sql` 已删除 **`t_resource_invoke_grant`**、**`t_resource_grant_application`**。`ResourceInvokeGrantService.ensureApiKeyGranted` **不再**按 per-resource Grant 或 `t_resource.access_policy` 拦截 invoke（见该类注释）。**当前消费侧**以 **有效 `X-Api-Key`、Key scope、资源 `published`（及 owner/平台 Key 等规则）** 为主。下文 **§2.3、§2.4、§四** 中关于 Grant 短路、`access_policy` 免 Grant 的表格描述多为 **迁移前** 设计快照；**请以** `PRODUCT_DEFINITION.md` §4、`docs/api/public-catalog-contract.md` **与代码为准**。

---

## 一、角色与载体（共同前提）

| 载体 | 作用（现状代码中的主要体现） |
|------|------------------------------|
| **JWT / ` X-User-Id`** | 控制台操作、审核、用户管理、资源注册 CRUD 等。 |
| ** `X-Api-Key`** | 目录 `catalog` / `resolve`、网关 `invoke` / `invoke-stream`；与 `ResourceInvokeGrantService`、Key scope 绑定。 |
| **Casbin（`RequireRole` / `RequirePermission`）** | 控制「谁能调哪个 Controller 方法」；与 `GatewayUserPermissionService`（按资源类型读权限）并行存在。 |
| **无角色用户** | `UnassignedUserAccessFilter`：无 `user_role_rel` 时仅允许白名单路径（入驻申请、账号类等）。 |

---

## 二、现状权限流程（摘要）

### 2.1 未赋权账号（无角色）

| 维度 | 行为 |
|------|------|
| **后端** | `UnassignedUserAccessFilter`：无角色则仅 `/developer/applications/**`、`/auth/*`（部分）、健康检查等可走，其余 **403**（提示「未赋权账号仅可提交开发者入驻申请」）。 |
| **产品含义** | 与「先入驻再办事」一致；实际仍包含登录、资料等必要接口，不单列一个 URL。 |

### 2.2 资源审核（部门 / 平台）

| 阶段 | 角色（`AuditController`） | 服务层行为（`AuditServiceImpl` 注释与实现） |
|------|---------------------------|-----------------------------------------------|
| 列表与 **approve / reject** | `platform_admin` **或** `dept_admin` | `dept_admin`：仅能看到 **本部门提交人** 的审核项（`DeptScopeHelper.isDeptAdminOnly` + `submitter in 部门用户`）。`platform_admin`：全量。 |
| **publish（上线发布）** | `platform_admin` / `admin` / **同资源代管范围**（owner、同 menu 的 dept_admin） | 将状态从 `testing` → `published`；服务层 `ensureMayPublishAuditedResource`（与历史 Grant 代管无表级耦合）。 |
| **平台强制下架** | `platform_admin` | `POST /audit/resources/{id}/platform-force-deprecate` → `deprecated`；与开发者自助 `resource-center/.../deprecate` 区分。 |

结论：**部门已部分专精（只看本部门队列）**；上线发布已与 **资源拥有者 / 本部 dept_admin / 平台** 对齐；超管保留 **跨租户强制下架**。

### 2.3 资源授权（Grant）— **已整体下线（2026-04-09）**

`/resource-grants*`、`/grant-applications*` 与对应表已删除；**不再**存在 per-resource Grant CRUD 或工单审批主路径。消费可见性由 **API Key、scope、`published`** 与网关实现承担。方法名 `ResourceInvokeGrantService.ensureApiKeyGranted` 等为历史命名，类注释已说明不再读 Grant 表。

### 2.4 网关消费（目录 / resolve / invoke）

| 环节 | 行为（与角色并行） |
|------|---------------------|
| **登录用户读目录类型** | `GatewayUserPermissionService`：非 `platform_admin` 时需具备如 `skill:read`、`agent:read` 等（按资源类型组合）；**与「是否开发者」解耦，由 Casbin 权限决定**。 |
| **带 API Key 的 catalog / resolve / invoke** | `UnifiedGatewayServiceImpl`：`apiKeyScopeService` + **`ResourceInvokeGrantService.ensureApiKeyGranted`**（platform key / owner key / Key 归属 owner 等短路）；**不再**查 Grant 表或 `access_policy` 做拦截。 |
| **`t_resource.access_policy`** | **历史字段**（迁移后多为 `open_platform`）；**不再**作为 invoke 的「免 Grant / 须 Grant」开关。注册侧固定写入见 `ResourceRegistryServiceImpl`。 |

结论：**invoke 真值**以 **有效 API Key + scope + 资源生命周期** 为主；勿再按 §2.4 迁移前的 `access_policy` 叙事排查 Grant。

### 2.5 超管日常占用的能力（不完全列举）

| 区域 | 典型 `@RequireRole` |
|------|---------------------|
| 系统参数、配额、限流、模型配置、公告 | `platform_admin` |
| 监控 / 健康洞察 | `platform_admin` |
| 敏感词、部分标签管理 | `platform_admin` |
| 用户管理部分接口 | `platform_admin` ± `dept_admin` |

结论：**平台配置与监控** 已在超管侧。Grant 工单已下线；**publish** 权已下放至 owner/本部 dept_admin/平台（见审核服务），超管在日常消费上不必再充当唯一入口。

---

## 三、目标权限流程（与你们讨论对齐）

下列为**目标态产品叙事**，用于和第二节逐条对比；实施时需落到策略枚举与代码分支。

| 主题 | 目标 |
|------|------|
| **无角色用户** | 维持「以入驻为主 + 必要账号能力」；白名单可按产品收紧/扩展。 |
| **开发者** | 对自己 `created_by` 的资源：**注册/编辑/提交审核**；**自主决定消费策略**（如：需 Grant / 对已发布资源开放组织内或平台内免 Grant 等——以明确策略为准）；**主动授权**他人 API Key（Grant）为**主路径**；在部门审核通过后 **自行上线/下架**（或仅在策略允许范围内操作，见下）。 |
| **部门管理员** | **本部事宜**：审核本部提交、查看本部相关工单与数据；**Grant 审批**若保留工单，应 **优先** 路由到 **资源 owner** 或 **本部协调**；**缩小**「对任意 owner 资源的 Grant 管理权」至 **本部开发者资源**（若保留部门代管能力）。 |
| **平台管理员** | **系统配置、监控、风控、跨部门兜底**：全局参数、告警、强制下架、违规处置；**不再**作为日常 Grant 与每张资源上线的唯一入口。 |

---

## 四、对比总表（现状 → 目标）

| 能力域 | 现状（代码） | 目标（讨论） |
|--------|--------------|--------------|
| 无角色访问 | 过滤器白名单，已对齐大方向 | 可继续细化是否暴露 swagger 等 |
| 审核队列可见范围 | 部门管理员已按部门过滤 | 保持；平台管理员全量 |
| **资源发布 publish** | owner / 同部门 dept_admin / platform_admin、admin（服务层校验） | **与 Grant 代管范围一致的自发布**；超管兜底 |
| **Grant 直接写入** | owner + dept_admin + platform_admin | **主路径：owner**；dept_admin **限定本部资源**；超管兜底 |
| **Grant 申请待办/审批** | 仅 `platform_admin`，且无部门过滤 | **owner 审批** 或 **部门审批（限本部）**；超管兜底/升级 |
| **公开消费（当前）** | **无 Grant 表**：`ensureApiKeyGranted` 仅保留 owner/平台 Key 等规则；**不**再按 `access_policy` 分支 | 组织级可见若需细化，应在 **Key scope 产品规则** 落实（见 `PRODUCT_DEFINITION.md` §4） |
| 网关读权限 | Casbin 按资源类型 | 可与「开发者 / 消费者」角色继续对齐 |
| 开发者看自身统计 | 需专门接口与埋点（下载与 invoke 不同源） | **按 owner 维度的仪表**（调用、下载等分列） |

---

## 五、修改计划（建议分阶段）

阶段颗粒度可按人力拆分；**建议先做权限与数据模型，再改前端引导**。

### 阶段 A：产品策略与数据模型（阻塞项）— **已完成（2026-04-03）**

1. 枚举与库字段：`ResourceAccessPolicy` + `t_resource.access_policy`（Flyway `V6__resource_access_policy.sql`；全量基线见 `sql/lantu_connect.sql`）。  
2. API：`ResourceUpsertRequest.accessPolicy`（可选，缺省/空=创建时 `grant_required`；**更新时未传则保留原值**）、`ResourceManageVO` / `ResourceCatalogItemVO` 回传。  
3. 文档：`PRODUCT_DEFINITION.md` §4、本文；实施详情见 `docs/resource-registration-authorization-invocation-guide.md` §3.1。**网关按策略短路 Grant 属阶段 B**。

> **读档提示（2026-04-09）**：自 **`t_resource_invoke_grant` / 工单表删除** 起，下方 **阶段 B、C** 等仅为 **迁移前实施存档**；其中 **`grant_required` 表行校验、工单路由、`ensureMayReviewGrantApplication`** 等 **不再**对应线上可调 API。真值见文首 §真值变更 与 §2.3–2.4。

### 阶段 B：网关与 Grant 核心逻辑 — **已完成（2026-04-03）**（历史存档）

1. **`ResourceInvokeGrantService.ensureApiKeyGranted`**：`access_policy=open_platform` 时免 Grant（仍走上层 Key 与 scope）；`open_org` 时仅当 **API Key 为 user 归属**且 **Key 所属用户与资源 owner 的 `menuId` 相同**时免 Grant；`grant_required` 不变。技能包下载等走同一方法的路径一并生效。  
2. **`catalog_read`**：与 grant 表中的 `catalog` 动作对齐（校验时视同为 `catalog`）。  
3. **`ensureCanManageGrant`**：`dept_admin` 仅当 **资源 owner 与操作者 `menuId` 相同** 可管理 Grant；`platform_admin` / `admin` 仍全局；owner 本人不变。

### 阶段 C：Grant 工单路由 — **已完成（2026-04-03）**（历史存档；路由已删）

1. `GET /grant-applications/pending`：凭 `X-User-Id` 过滤待办 — **platform_admin/admin** 全量；**dept_admin（且非平台）** 仅 **资源 owner 与本部门 `menu_id` 一致** 的申请；**其他用户** 仅 **自己名下资源**（`t_resource.created_by`）上的申请。  
2. `POST .../approve`、`reject`：**不再限定 platform_admin**；由 `ResourceInvokeGrantService.ensureMayReviewGrantApplication` 校验（与 Grant 管理同款：owner / 同部门 dept_admin / 平台）。  
3. **新申请通知**：除原平台管理员广播外，**额外通知资源 owner**。

### 阶段 D241：**审核与发布权** — **已完成（2026-04-03）**

1. `AuditController`：`publish*` `@RequireRole({"platform_admin","admin","dept_admin","developer"})`；**发布权**由 `ResourceInvokeGrantService.ensureMayPublishAuditedResource` 收紧（owner / 同 menu dept_admin / platform_admin、admin）；仍仅 **testing → published**。  
2. `POST /audit/resources/{id}/platform-force-deprecate`：**仅** `platform_admin`，资源 → `deprecated`，审核队列表记可选标记 `platform_force_deprecated`，并通知 owner（`platform_resource_force_deprecated`）。  
3. 开发者自助下线仍用 `POST /resource-center/resources/{id}/deprecate`（owner/管理员既有逻辑）。  

### 阶段 E：Casbin 与无角色白名单 — **已完成（2026-04-03）**

1. **developer** 角色已与 `GatewayUserPermissionService` 对齐：`agent`→`agent:read`∨`skill:read`；**`mcp` 与 `skill` 共用 `skill:read`**（代码注释已写明）；`app`→`app:view`；`dataset`→`dataset:read`。资源注册接口本身不挂细粒度 Casbin，由 owner/状态机约束。  
2. 新增 **`consumer`** 系统角色（Flyway `V7__consumer_role_and_catalog_alignment.sql` + `sql/lantu_connect.sql`）：`agent:read`、`skill:read`、`app:view`、`dataset:read`，供「只逛市场」账号；`/catalog/resources/trending` 与 `/search-suggestions` 已按**同一类型谓词**过滤（与主目录一致）。  
3. **`lantu.security.expose-api-docs`**：`SecurityProperties` 默认值改为 **false**，与 `application.yml` 主配置一致；关文档时 `SecurityConfig` 与 `UnassignedUserAccessFilter` 均不放开 Swagger（未赋权账号不暴露 OpenAPI）。dev profile 仍可覆盖为 `true`。  

### 阶段 F：开发者统计 — **已完成（2026-04-03）**

1. **`GET /dashboard/owner-resource-stats`**：`periodDays`（默认 7）、可选 `ownerUserId`。聚合 **`t_call_log`**（归属 owner 资源的网关调用）、**`t_usage_record`**（`action=invoke` 且可归因到资源）、**`t_skill_pack_download_event`**（技能包下载埋点）。  
2. **权限**：本人；**同 `menu_id` 的 dept_admin** 只读他人；**platform_admin/admin** 可查任意 owner。不依赖 `monitor:view`。  
3. **Flyway `V8`**：`t_usage_record.resource_id`（invoke 写入）；新表 **`t_skill_pack_download_event`**；成功流式下载技能包后由 `SkillArtifactDownloadService` 记一行。  

### 阶段 G：回归与安全 — **已完成（2026-04-03）**

1. **`ResourceInvokeGrantServicePolicyTest`**：`open_platform` 免 Grant；`grant_required` 无 Grant 则拒绝；**dept_admin** 跨 `menu_id` 不可审 Grant 工单（`ensureMayReviewGrantApplication`）。  
2. **`AuditLog`**：`AuditController` 的 approve / reject / publish（agent/skill/resource）及既有 `platform_force_deprecate`；Grant 工单 approve/reject 保持原有注解。

---

## 六、修订记录

| 日期 | 说明 |
|------|------|
| 2026-04-03 | 初稿：现状来 自 `UnassignedUserAccessFilter`、`AuditController`/`AuditServiceImpl`、`GrantApplicationController`、`ResourceInvokeGrantService`、`UnifiedGatewayServiceImpl` 等；目标与计划对接前述产品讨论。 |
| 2026-04-03 | 阶段 A 落库：`access_policy`、DTO、`ResourceAccessPolicy`。 |
| 2026-04-03 | 阶段 B：`ResourceInvokeGrantService` 开放策略短路、`catalog_read` 映射、`dept_admin` Grant 管理限定同 menuId。 |
| 2026-04-03 | 阶段 C：Grant 工单待办按角色过滤、审批权下放、owner 通知。 |
| 2026-04-03 | 阶段 D241：发布权下放至 owner/本部代管、平台强制下架接口。 |
| 2026-04-03 | 阶段 E：`consumer` 角色、trending/搜索按目录权限过滤、`exposeApiDocs` 默认 false。 |
| 2026-04-03 | 阶段 F/G：owner 统计 API、技能下载埋点、Grant/开放策略测试、审核 AuditLog 全覆盖。 |
| 2026-04-09 | Grant 表删除、`access_policy` 不再驱动 invoke；本文 §2.3–2.4、§四 已加真值提示，历史阶段 B/C 描述仅供参考。 |

---

*若要落地某一阶段，可将对应小节拆成 issue，并在 PR 描述中引用本文档章节。*
