# 五类资源主功能审查 — 2026-04-15

**工作流配合**：Superpowers（会话节奏）+ 个人 `~/.cursor/skills`（`full-project-audit-runbook`、`backend-capability-closure-audit`、`api-contract-and-dto-audit`、`data-display-clarity-audit`、`rbac-api-permissions` 等）+ 项目 [.cursor/skills/lantuconnect-backend-audit/SKILL.md](../../.cursor/skills/lantuconnect-backend-audit/SKILL.md)。  
**外部产品设计类 rubric**：可在 Cursor 市场启用清单型 Skill，或使用本平台的 [`package-import-url`](../../src/main/java/com/lantu/connect/gateway/controller/ResourceRegistryController.java) 拉取公开 SKILL 仓库 **仅作只读审查清单**（勿与生产数据混用）。

---

## 1. 五类资源 × 能力闭环矩阵

资源类型集合：`agent` | `skill` | `mcp` | `app` | `dataset` — [`ResourceRegistryServiceImpl.RESOURCE_TYPES`](../../src/main/java/com/lantu/connect/gateway/service/impl/ResourceRegistryServiceImpl.java)。

状态机：[`ResourceLifecycleStateMachine`](../../src/main/java/com/lantu/connect/gateway/service/support/ResourceLifecycleStateMachine.java)（draft / pending_review / testing / published / rejected / deprecated）。

| 类型 | 注册 / 扩展表 | 生命周期 + 审核 | 消费侧（目录 / 解析 / 调用） | 监控与可观测 |
|------|----------------|-----------------|-----------------------------|----------------|
| **agent** | **闭环**：`upsertAgentExt` 必填 `agentType`、非空 spec；[`t_resource_agent_ext`](../../src/main/java/com/lantu/connect/gateway/service/impl/ResourceRegistryServiceImpl.java) | **闭环**：与同型资源共用 `submitForAudit` → `AuditServiceImpl`；乐观锁更新 `t_resource` | **闭环**：`resolveAgent` → endpoint + spec；[`invoke`](../../src/main/java/com/lantu/connect/gateway/service/impl/UnifiedGatewayServiceImpl.java) 走协议注册表；需 **published** + endpoint | **部分**：`include=observability|quality`；[`t_call_log`](../../src/main/java/com/lantu/connect/gateway/service/impl/UnifiedGatewayServiceImpl.java) 按 agent 统计；Registry [`observability-summary`](../../src/main/java/com/lantu/connect/gateway/controller/ResourceRegistryController.java) |
| **skill** | **闭环**：`upsertSkillExt`；**禁止**将 `skill_type` 设为 mcp/http_api 包装（[`FORBIDDEN_SKILL_PACK_TYPES`](../../src/main/java/com/lantu/connect/gateway/service/impl/ResourceRegistryServiceImpl.java)）；提交审核前须有 **artifact_uri**（`submitForAudit` 内校验） | **闭环**：同上 | **刻意非 invoke 闭环**：[`ensureSkillNotInvokable`](../../src/main/java/com/lantu/connect/gateway/service/impl/UnifiedGatewayServiceImpl.java) — 技能包 **不允许** `/invoke`，由 Agent 运行时加载；**resolve** 返回 artifact 或 `artifactDownloadApi`；[`skill-artifact`](../../src/main/java/com/lantu/connect/gateway/controller/ResourceRegistryController.java) 受控下载 | **部分**：pack_validation_* 字段；质量块可走 `include`；调用统计对 skill 不如 agent 直接 |
| **mcp** | **闭环**：`upsertMcpExt` endpoint + protocol | **闭环**：同上 | **闭环**：`resolveMcp`；`invoke` / `invoke-stream`（仅 MCP 类）；缺 ext 则 **NOT_FOUND** | **部分**：同 agent 类治理与 call_log |
| **app** | **闭环**：`upsertAppExt` app_url 等；**resolveApp** 拼 embed/icon/screenshots | **闭环**：同上 | **闭环**：解析阶段 **剥离真实 app_url**（[issueAppLaunchTicket](../../src/main/java/com/lantu/connect/gateway/service/impl/UnifiedGatewayServiceImpl.java)）；需 **本人用户级 API Key** 换 launchUrl | **部分**：与 redirect 模式相关日志 |
| **dataset** | **闭环**：`upsertDatasetExt` 元数据字段 | **闭环**：同上 | **仅元数据闭环**：[`resolveDataset`](../../src/main/java/com/lantu/connect/gateway/service/impl/UnifiedGatewayServiceImpl.java) 的 `endpoint` 为 **null**，`invokeType=metadata`；**invoke** 会因无 endpoint **失败** — 设计上是 **不可远程执行的资料型资源**，须在文档/产品上写清 | **部分**：无调用链；可依赖 favorite / review / 浏览 |

### 闭环结论摘要

- **P0 缺口**：无（skill 不 invoke、dataset 无 endpoint 均为明确产品设计，非代码断链）。
- **P1 提示**：dataset 若产品期望「可下载文件」，需单独 API（当前 resolve 仅有 data_type/format/record_count 等，无文件 URL 字段在本轮未扩查）。
- **鉴权横切**：catalog / resolve / invoke 与 `access_policy`、Grant、API Key scope — [`ResourceInvokeGrantService`](../../src/main/java/com/lantu/connect/gateway/security/ResourceInvokeGrantService.java)、[`GatewayUserPermissionService`](../../src/main/java/com/lantu/connect/gateway/security/GatewayUserPermissionService.java)。

---

## 2. 产品 / API 展示细度（契约与信息架构）

对照 rubric：**列表是否要少次请求拼齐作者/评分/评论**、**详情是否自洽**、**评论是否分页**。

### 2.1 主要读 API 与字段

| API / VO | 作者 / 所有者 | 评分 | 评论 | 备注 |
|----------|----------------|------|------|------|
| `GET /catalog/resources` → `ResourceCatalogItemVO` | **本轮起**：`createdBy` + `createdByName`（原 SQL 有 `created_by` 但未映射） | **本轮起**：`ratingAvg`、`reviewCount` | 仍须 **`GET /reviews`** 拉列表；目录不内嵌评论正文 | `include=observability,quality` 见 [`attachIncludesToCatalogItems`](../../src/main/java/com/lantu/connect/gateway/service/impl/UnifiedGatewayServiceImpl.java) |
| `GET /catalog/resources/trending` → `ExploreResourceItem` | **已对齐**：`author`（`UserDisplayNameResolver`）、`reviewCount`、`favoriteCount`；**修正** `t_review` / `t_favorite` 与 `(resource_type, id)` 关联（原仅 `target_id` 有误） | 无评论时 `rating` 为 null | 无 | 见 [`UnifiedGatewayServiceImpl#trending`](../../src/main/java/com/lantu/connect/gateway/service/impl/UnifiedGatewayServiceImpl.java) |
| `GET /resource-center/resources/mine` → `ResourceManageVO` | **有** `createdBy`、`createdByName` | 代理扩展表有 `rating_avg`（agent 列表用） | 无内嵌 | 管理端最全 |
| `GET /catalog/resources/{type}/{id}` → `ResourceResolveVO` | **已有** `createdBy`、`createdByName`（[`findResourceBase`](../../src/main/java/com/lantu/connect/gateway/service/impl/UnifiedGatewayServiceImpl.java) 选 `created_by` + 解析） | **无**（须 `/stats`） | 无 | **invokeType/endpoint/spec** 按类型填充 |
| `GET /catalog/resources/{type}/{id}/stats` → `ResourceStatsVO` | 无 | **有** rating | 无 favorite 在 stats—有 favoriteCount | 前端拼详情需 **2～3 次请求** |
| `GET /reviews`、`GET /reviews/page` | N/A | N/A | **有** [`Review`](../../src/main/java/com/lantu/connect/review/entity/Review.java) 含 `userName`、`avatar`、`rating`、`comment`、`helpfulCount` | 全量 `GET /reviews`；分页 **`GET /reviews/page`** → [`PageResult`](../../src/main/java/com/lantu/connect/common/result/PageResult.java)；[`ReviewServiceImpl#pageList`](../../src/main/java/com/lantu/connect/review/service/impl/ReviewServiceImpl.java) 将 `pageSize` 限制为 **1～100** |

### 2.2 分级 backlog（展示 / 契约）

| ID | 级别 | 说明 |
|----|------|------|
| DISP-1 | **已做（P1）** | 目录分页补 **作者 + 评分摘要**：[`ResourceCatalogItemVO`](../../src/main/java/com/lantu/connect/gateway/dto/ResourceCatalogItemVO.java)、[`UnifiedGatewayServiceImpl#catalog`](../../src/main/java/com/lantu/connect/gateway/service/impl/UnifiedGatewayServiceImpl.java) |
| DISP-2 | **已做** | `trending` 补齐作者/收藏数/评论数；修复 review 与 favorite 的复合键关联 |
| DISP-3 | **已做** | `ResourceResolveVO` 增加 `createdBy`、`createdByName`；[`ResourceResolveSpecSanitizer`](../../src/main/java/com/lantu/connect/gateway/dto/ResourceResolveSpecSanitizer.java) 透传 |
| DISP-4 | **已做** | [`ReviewController`](../../src/main/java/com/lantu/connect/review/controller/ReviewController.java) `GET /reviews/page`；`GET /reviews` 仍全量以兼容旧客户端 — 迁移完成后可标删或 _deprecated |
| API-1 | **已交付（P2）** | 对外 **OpenAPI**（`OpenApiConfiguration` + 目录/评论/SDK 注解与 DTO `@Schema`）与契约说明 **[public-catalog-contract.md](../api/public-catalog-contract.md)**（`include`、`access_policy`、推荐调用链）；默认环境仍关闭 springdoc，本地 profile 可开。 |

---

## 3. 验证

- 代码变更后：`mvn -q test`。
- 集成：目录接口抽检 `createdByName`、`ratingAvg`、`reviewCount` 与非空资源一致。

---

## 4. 参考路径

- 注册入口：[ResourceRegistryController](../../src/main/java/com/lantu/connect/gateway/controller/ResourceRegistryController.java)
- 目录 / 解析 / 调用：[ResourceCatalogController](../../src/main/java/com/lantu/connect/gateway/controller/ResourceCatalogController.java)、`UnifiedGatewayServiceImpl`
- 审核：[AuditController](../../src/main/java/com/lantu/connect/audit/controller/AuditController.java)、`AuditServiceImpl`
- 评论：[ReviewController](../../src/main/java/com/lantu/connect/review/controller/ReviewController.java)

---

## 5. 维护记录 — 2026-04-04（Superpowers + 多技能复审）

**工作流**：`using-superpowers`（拆解与验证）+ 项目 [lantuconnect-backend-audit](../../.cursor/skills/lantuconnect-backend-audit/SKILL.md) + `backend-capability-closure-audit`、`rbac-api-permissions`、`api-contract-and-dto-audit`、`data-display-clarity-audit`；产品向 rubric 对齐 PRD/前端信息架构（本机 `prd`、`frontend-design` 等）。

### 5.1 §1 矩阵复检（闭环）

| 类型 | 结论 | 证据（节选） |
|------|------|----------------|
| agent / mcp | **仍闭环** | 注册 [`ResourceRegistryServiceImpl`](../../src/main/java/com/lantu/connect/gateway/service/impl/ResourceRegistryServiceImpl.java)；解析/调用 [`UnifiedGatewayServiceImpl`](../../src/main/java/com/lantu/connect/gateway/service/impl/UnifiedGatewayServiceImpl.java)；生命周期 [`ResourceLifecycleStateMachine`](../../src/main/java/com/lantu/connect/gateway/service/support/ResourceLifecycleStateMachine.java) + [`AuditServiceImpl#publish`](../../src/main/java/com/lantu/connect/audit/service/impl/AuditServiceImpl.java) |
| skill | **仍闭环（非 invoke 为设计）** | [`ensureSkillNotInvokable`](../../src/main/java/com/lantu/connect/gateway/service/impl/UnifiedGatewayServiceImpl.java) |
| app | **仍闭环** | `issueAppLaunchTicket` 等同路径仍在 `UnifiedGatewayServiceImpl` |
| dataset | **仍闭环（仅元数据）** | [`resolveDataset`](../../src/main/java/com/lantu/connect/gateway/service/impl/UnifiedGatewayServiceImpl.java)；P1：若产品要「可下载文件 URL」，仍属增量能力（与 §1 P1 一致） |

**鉴权横切（本轮重点对账）**：`t_resource.access_policy` + [`ResourceInvokeGrantService#ensureApiKeyGranted`](../../src/main/java/com/lantu/connect/gateway/security/ResourceInvokeGrantService.java)（`isGrantWaivedByAccessPolicy`：`open_platform` / `open_org` / `grant_required`）。文档 **§2.4 旧稿** 称「无 accessPolicy 短路」**已与实现不符**，已在 [permission-flow-comparison-and-plan.md](../permission-flow-comparison-and-plan.md) **§2.4、§四** 修订。

### 5.2 产品 / 展示细度（契约与 IA）

- **目录**：[`ResourceCatalogItemVO`](../../src/main/java/com/lantu/connect/gateway/dto/ResourceCatalogItemVO.java) 含 `createdBy`、`createdByName`、`ratingAvg`、`reviewCount`、`accessPolicy`、`tags`；列表**不内嵌评论正文**（减少 payload）— 合理；评论须 **`/reviews` 或 `/reviews/page`**。
- **详情 + 口碑**：`ResourceResolveVO` 含作者；**评分**仍在 **`/stats`**（[`ResourceStatsVO`](../../src/main/java/com/lantu/connect/gateway/dto/ResourceStatsVO.java)）；前端典型 **2～3 次请求**（resolve + stats + 评论页）。**API-1** 已交付：SpringDoc 与 [`public-catalog-contract.md`](../api/public-catalog-contract.md)。
- **分页评论**：[`ReviewController` `GET /reviews/page`](../../src/main/java/com/lantu/connect/review/controller/ReviewController.java)，`pageSize` 1～100（[`ReviewServiceImpl#pageList`](../../src/main/java/com/lantu/connect/review/service/impl/ReviewServiceImpl.java)）。

**每类资源 — 用户故事与验收（PRD 视角，摘要）**

| 类型 | 用户故事（摘录） | 验收要点 |
|------|------------------|----------|
| agent | 作为消费者，我要在已发布后通过 Key 解析并 invoke Agent | published + endpoint；无 Grant 时 `access_policy` 或 Grant 满足；错误信息可读 |
| skill | 作为消费者，我要下载技能包而不能误用 invoke | invoke 返回明确禁止；resolve 含制品/下载 API |
| mcp | 作为消费者，我要对 MCP 走 invoke 或流式 | 协议与熔断路径可查 `UnifiedGatewayServiceImpl` |
| app | 作为消费者，我要安全拿到 launch 地址而非暴露 owner URL | launch ticket + 用户级 Key |
| dataset | 作为消费者，我要看到数据集元数据且不期望远程 execute | resolve 无 endpoint；产品文案与 400 一致 |

### 5.3 分级结论（P0 / P1 / P2）

| 级别 | 项 | 说明 |
|------|-----|------|
| **P0** | 无新增 | §1 矩阵无回归；**权限文档与实现对齐**已完成（permission-flow） |
| **P1** | dataset 文件分发 | 若产品要直连下载，需新 API/字段（历史 P1 仍有效） |
| **P2** | API-1 | **已交付**：OpenAPI + [`public-catalog-contract.md`](../api/public-catalog-contract.md)（`include`、`access_policy`、多请求组合） |

### 5.4 验证

- `mvnw test`：本次执行 **通过**（2026-04-04）。
- 集成抽检（2026-04-03，本机 `localhost:8080`，`context-path=/regis`）：
  - **准备（仅用于验证，事后已还原）**：向 `t_api_key` 临时写入一条已知明文的用户 Key（`owner_id=3`，`scopes=["*"]`）；将已发布资源 **skill `id=54`** 的 `access_policy` 置为 **`open_platform`**（owner 为用户 1，与 Key 用户 3 非同一人）；将 **mcp `id=36`** 的 `access_policy` 置为 **`open_org`** 且 **`created_by=2`**（用户 2 与 3 同属 `menu_id=2`，与 Key 用户 3 仍非同一人，符合 org 内豁免、非 owner 绕行）。
  - **请求**：`POST /regis/catalog/resolve`，Header `X-Api-Key`，Body `{"resourceType":"mcp"|"skill","resourceId":"36"|"54"}`。
  - **结果**：两次均 **HTTP 200**，`code=0`，`data` 为对应类型的 `ResourceResolveVO`（Grant 未阻拦；与 `ResourceInvokeGrantService#isGrantWaivedByAccessPolicy` 一致）。完成后已将该 Key 行删除，并将两条资源的 `access_policy`/`created_by` **恢复为原值**。
- 其他集成建议：抽检 catalog 的 `createdByName`/`ratingAvg`/`reviewCount`。
