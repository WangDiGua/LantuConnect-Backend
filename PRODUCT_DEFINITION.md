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
| 规范与制品 / 开发赋能 | `skill` | **双形态**：**技能包（`execution_mode=pack`）** 通过 **`artifact_uri` / `…/skill-artifact` 下载**，**禁止**网关 `invoke`；**托管技能（`execution_mode=hosted`）** 由平台保存提示模板并 **允许** `invoke`（代调 LLM，见 `lantu.hosted-llm`）。远程可执行工具仍应注册 **`mcp`**。 |

---

## 3. 消费方式总览

| 类型 | 统一网关 `invoke` | 其他典型消费方式 |
|------|-------------------|------------------|
| `agent` | 支持（需 `published`、合法 endpoint、协议受支持） | `resolve` 查看协议与端点 |
| `skill`（pack） | **不支持** | `resolve` + **制品下载**；宿主侧加载 |
| `skill`（hosted） | **支持**（`published`、Hosted LLM 已配置） | `resolve` 查看参数/schema；**无**上游 endpoint |
| `mcp` | 支持；流式用 `invoke-stream`（MCP）；可配置 **前置 Hosted Skill** 洗参 | `resolve` 查看 endpoint 与鉴权 spec |
| `app` | 支持，但为 **redirect/票据** 语义 | **`resolve` 获取 launch 信息**；浏览器打开/嵌入 |
| `dataset` | **不支持**（无 invoke endpoint） | **`resolve` 读元数据**；若需申请/下载数据文件，由**独立能力或扩展**承载 |

### 3.1 绑定与 invoke 展开（Agent / MCP / Hosted Skill）

- **无绑定**：`invoke` 行为与未登记关联边时一致，请求体不会被网关附加绑定信息。
- **`agent` + `agent_depends_mcp`**：用户仅 `POST /catalog/invoke`（或 SDK 同源路径）调用 Agent 时，网关在对上游 Agent `endpoint` 转发前，对**已绑定且当前 Key 可 invoke** 的各 MCP 执行与「聚合工具」一致的 **`tools/list`**，将结果写入请求 JSON 保留命名空间 **`_lantu.bindingExpansion`**（含 `entry`、`openAiTools`、`routes`、`warnings`）。**不**代上游自动执行 `tools/call`；Agent 实现自行决定是否消费 tools。开关见 `lantu.gateway.binding-expansion`。
- **单独 `mcp` invoke**：**不会**根据绑定反向拉起 Agent；若登记 **`mcp_depends_skill`**，仍在网关内先跑前置 Hosted Skill 链（与既有行为一致）。
- **`skill`（hosted）+ `mcp_depends_skill`（MCP→Skill）**：逆查绑定到该技能的 MCP；对它们做同上 **`tools/list`** 聚合并写入 **`_lantu.bindingExpansion`** 后再进入 Hosted LLM **单次** chat。**当前阶段不**实现 Hosted LLM 的多轮 `tool_calls` 自动回环；与「仅 MCP 侧执行前置 Skill 链」形成产品上的 Skill↔MCP 互补说明。
- **权限**：展开涉及的每个 MCP 均须在当前 **X-Api-Key** 的 scope 内具备 **invoke** 能力，否则对应 MCP 记入 `warnings` 而非阻断整个请求（与聚合工具 BFF 一致）。

---

## 4. 与「注册平台」的关系

- **是**「注册 + 目录 + 授权 + 消费出口」一体化的平台：生命周期（登记 → 审核 → 发布）、目录、`resolve`、**API Key / scope**、网关与审计等均围绕此展开。其中 **`testing → published` 的发布动作**由资源 **owner**、**与 owner 同部门的 dept_admin** 或 **platform_admin** 执行；**平台跨租户强制下架**为单独的 `platform-force-deprecate` 能力（详见 `docs/permission-flow-comparison-and-plan.md` 阶段 D241）。
- **系统四类角色（平台 RBAC）**：
  - **平台管理员**：管理平台全局事宜（用户、组织、角色、系统配置、全平台资源与审核等）。
  - **部门管理员**：管理对应部门的**开发者与消费者**，以及本部门范围内的资源协同与审核边界。
  - **开发者**：负责**五类资源的登记、维护、审核流中与「自己的资源」相关的发布与修订**；不包含把「全校师生默认」都当作开发者。
  - **消费者**：有权**使用已上架的五类资源**（浏览目录、个人 API Key、`resolve`/网关等约定消费路径；**per-resource Grant/工单已下线**，消费以 Key、scope、`published` 为准）；**可申请开发者入驻**；**个人资料、改密、登录历史**等账号能力对其开放；Casbin 侧目录读权限与 `GatewayUserPermissionService` 一致（**`mcp` 与 `skill` 共用 `skill:read`**）。自助注册默认绑定 **consumer**（见 `AuthServiceImpl.register`）。
- **不是**「所有条目都同质可 RPC」的纯服务注册中心：`skill` 与 `dataset` 的分叉是**领域设计**，符合门户对**资产多样性**的预期。
- **资源消费策略（`t_resource.access_policy`）**：库字段可能仍为 `grant_required` / `open_org` / `open_platform`。**统一网关 invoke/目录裁决以 API Key scope + `published` 与资源可见性为准**（`ResourceInvokeGrantService` 不再读取 `t_resource_invoke_grant`）。产品若仍需组织级开放语义，应在 **Key scope 产品规则** 上落实，而非 Grant 表。

---

## 5. 调用统计与「消费量」的认知边界（避免误解）

当前实现里，监控/工作台常用的 **`t_call_log`** 与 **`t_usage_record`（`action=invoke`）** 主要在 **`UnifiedGatewayServiceImpl` 的 `invoke` / `invoke-stream` 成功路径** 写入，语义上更接近 **「统一网关远程调用一次」**，而不是「任何资产被使用一次」。

| 行为 | 是否进入上述调用统计 |
|------|----------------------|
| `agent` / `mcp` / `app`（redirect）经网关 **`invoke`** | **是**（在记录逻辑执行到的前提下） |
| **`skill` `resolve`、仅浏览目录** | **否** |
| **`dataset` `resolve`、元数据浏览** | **否** |
| **数据集文件下载**（若走独立文件/数据集接口） | **当前不等同于 invoke 统计**；是否另有 AccessLog/审计视具体接口而定 |
| **`app` 仅用浏览器打开 `launchUrl`，未走 `invoke`** | **通常不进** `CallLog`（可能仅有 **AccessLog** 等 HTTP 层日志，与「网关调用统计」不是一张表） |

因此：**数字上的「调用量」≠ 门户内全部数字化资产的使用量**。平台技能为 **hosted-only**；**`GET /dashboard/owner-resource-stats`** 当前汇总 **`t_call_log`** 与 **`t_usage_record`（`action=invoke`）**（详见实现代码与历史设计文档）。

部分界面用 `UsageRecord.type=skill` 推断「最近使用」：**hosted skill** 的 `invoke` 会产生记录。

---

## 6. 变更纪律

- 若调整 **资源消费策略枚举**、**可否 invoke**、**resolve 字段语义**、**绑定展开（`_lantu.bindingExpansion`）** 或 **skill/MCP 边界**，须同步：
  - [协议与接口手册](docs/frontend-alignment-handbook.md)
  - 相关 `docs/resource-registration-authorization-invocation-guide.md`、`docs/ai-handoff-docs/frontend-mcp-invoke-integration.md` 等读者文档
- **本文仅作产品锚点**；接口路径、请求头、错误码以手册与 Swagger 为准。
- 若调整 **调用/用量统计** 的写入点，须同步产品说明与相关仪表盘/报表文档，避免与本文 §5 矛盾。

---

*文档版本：与仓库主分支同步维护；重大产品结论变更应在本文件留简要修订说明或提交信息中注明。*
