# NexusAI Connect — 产品定义（锚点文档）

本文档用于固定 **产品边界与五类资源的定位**，避免实现与对外叙述漂移到「纯 API 网关」或「所有资源都可 invoke」等误解。细节契约仍以 [README.md](README.md) 所列 [docs/frontend-alignment-handbook.md](docs/frontend-alignment-handbook.md) 与代码为准。

---

## 1. 我们对标的产品形态

**企业内部 / 高校的「数字化资产与能力门户」**：统一登记、审核发布、目录发现、按权消费；条目不全是「可被 RPC 的微服务」，也包括应用入口、数据目录、开发者技能包等。

**一句话定位**：统一注册与目录的**数字化资产与可调用能力**——应用与数据以**发现与接入**为主，智能体与 MCP 以**统一网关调用**为主，技能包以**制品分发与开发赋能（如 vibe-coding）**为主。

---

## 2. 五类资源：门户语义 ↔ 类型

| 门户里常见的说法 | 资源类型 | 在平台中的角色 |
|------------------|----------|----------------|
| 业务能力 / 智能体服务 | `agent` | 可对外的能力入口；注册协议与 endpoint，走统一 **`invoke`**。 |
| 工具与集成 / 协议化远程服务 | `mcp`（及 agent 的 HTTP/REST 类） | 典型「工具」形态；网关代连上游 MCP 等，走 **`invoke` / `invoke-stream`**。 |
| 应用资产 / 轻应用 | `app` | 打开或嵌入页面；**`resolve`** 侧重 `launchUrl` / `launchToken`；需要时 **`invoke`** 走 **redirect** 语义，非普通业务 JSON-RPC。 |
| 数据资产 | `dataset` | **目录与元数据**（类型、格式、规模、标签等）；以 **`resolve`** 为主；**不是**通用远程执行资源（无统一 `invoke` endpoint）。 |
| 规范与制品 / 开发赋能 | `skill` | **技能包**（如 Anthropic zip、目录规范）；**禁止**统一网关 `invoke`；通过 **`artifact_uri` 或 `…/skill-artifact` 下载**，由 **Agent/IDE/宿主**加载。远程可调用工具应注册为 **`mcp`**，勿与 `skill` 混用。 |

---

## 3. 消费方式总览

| 类型 | 统一网关 `invoke` | 其他典型消费方式 |
|------|-------------------|------------------|
| `agent` | 支持（需 `published`、合法 endpoint、协议受支持） | `resolve` 查看协议与端点 |
| `skill` | **不支持**（代码显式拒绝） | `resolve` + **制品下载**；宿主侧加载 |
| `mcp` | 支持；流式用 `invoke-stream`（MCP） | `resolve` 查看 endpoint 与鉴权 spec |
| `app` | 支持，但为 **redirect/票据** 语义 | **`resolve` 获取 launch 信息**；浏览器打开/嵌入 |
| `dataset` | **不支持**（无 invoke endpoint） | **`resolve` 读元数据**；若需申请/下载数据文件，由**独立能力或扩展**承载 |

---

## 4. 与「注册平台」的关系

- **是**「注册 + 目录 + 授权 + 消费出口」一体化的平台：生命周期（登记 → 审核 → 发布）、目录、`resolve`、API Key / Grant、网关与审计等均围绕此展开。其中 **`testing → published` 的发布动作**由资源 **owner**、**与 owner 同部门的 dept_admin** 或 **platform_admin/admin** 执行（与 Grant 代管范围一致）；**平台跨租户强制下架**为单独的 `platform-force-deprecate` 能力（详见 `docs/permission-flow-comparison-and-plan.md` 阶段 D241）。**`consumer`** 系统角色仅含目录浏览类权限（与 `GatewayUserPermissionService` 一致，`mcp` 走 `skill:read`），用于「只逛市场」账号（阶段 E）。
- **不是**「所有条目都同质可 RPC」的纯服务注册中心：`skill` 与 `dataset` 的分叉是**领域设计**，符合门户对**资产多样性**的预期。
- **资源消费策略（`t_resource.access_policy`）**：开发者在注册/更新资源时可配置（默认 `grant_required`）。`grant_required`：须 per-resource Grant（及 Key scope）。`open_org`：用户归属的 API Key 且 Key 所属用户与资源 **owner 的部门（`menu_id`）一致** 时免 Grant。`open_platform`：租户内已认证 API Key 在满足 scope 前提下免 Grant。**仅 `published` 资源可被消费**的规则不变；开放策略不改变 skill/dataset 的 invoke 边界（见 §2–§3）。

---

## 5. 调用统计与「消费量」的认知边界（避免误解）

当前实现里，监控/工作台常用的 **`t_call_log`** 与 **`t_usage_record`（`action=invoke`）** 主要在 **`UnifiedGatewayServiceImpl` 的 `invoke` / `invoke-stream` 成功路径** 写入，语义上更接近 **「统一网关远程调用一次」**，而不是「任何资产被使用一次」。

| 行为 | 是否进入上述调用统计 |
|------|----------------------|
| `agent` / `mcp` / `app`（redirect）经网关 **`invoke`** | **是**（在记录逻辑执行到的前提下） |
| **`skill` 技能包下载** `GET …/resource-center/resources/{id}/skill-artifact` | **否**（`SkillArtifactDownloadService` 不写 `CallLog`/`UsageRecord`） |
| **`skill` `resolve`、仅浏览目录** | **否** |
| **`dataset` `resolve`、元数据浏览** | **否** |
| **数据集文件下载**（若走独立文件/数据集接口） | **当前不等同于 invoke 统计**；是否另有 AccessLog/审计视具体接口而定 |
| **`app` 仅用浏览器打开 `launchUrl`，未走 `invoke`** | **通常不进** `CallLog`（可能仅有 **AccessLog** 等 HTTP 层日志，与「网关调用统计」不是一张表） |

因此：**数字上的「调用量」≠ 门户内全部数字化资产的使用量**；技能包下载等需 **单独埋点**。当前实现：**技能包成功下载**写入 **`t_skill_pack_download_event`**（owner 维度）；网关 **invoke** 在 **`t_usage_record` 增加 `resource_id`** 便于按资源归属聚合；**`GET /dashboard/owner-resource-stats`** 汇总 call_log / usage_record(invoke) / 技能下载（详见 `docs/permission-flow-comparison-and-plan.md` 阶段 F）。

部分界面用 `UsageRecord.type=skill` 推断「最近使用」，在 **skill 禁止 invoke** 的现状下，**往往没有新增记录**，除非历史上存在其他写入路径或未来补上「下载/打开」类埋点。

---

## 6. 变更纪律

- 若调整 **资源消费策略枚举**、**可否 invoke**、**resolve 字段语义** 或 **skill/MCP 边界**，须同步：
  - [协议与接口手册](docs/frontend-alignment-handbook.md)
  - 相关 `docs/resource-registration-authorization-invocation-guide.md` 等读者文档
- **本文仅作产品锚点**；接口路径、请求头、错误码以手册与 Swagger 为准。
- 若调整 **调用/用量统计** 的写入点，须同步产品说明与相关仪表盘/报表文档，避免与本文 §5 矛盾。

---

*文档版本：与仓库主分支同步维护；重大产品结论变更应在本文件留简要修订说明或提交信息中注明。*
