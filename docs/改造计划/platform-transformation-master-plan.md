# 兰智通平台改造总计划（愿景 / 现状 / 差距 / 实施）

**规格冻结（Stage 0）**：[platform-transformation-spec-freeze.md](platform-transformation-spec-freeze.md)

---

## 一、文档目的与读者

- **目的**：把「要改成什么样的平台」写清楚，并对照「当前代码真实行为」，给出可执行的分阶段改造路线；不仅是开发排期，也是产品/架构/联调的共同锚点。
- **读者**：核心研发、前端、产品与对接方架构师。
- **范围**：聚焦 **`agent` / `mcp` / `skill`（Hosted）** 三类资源的绑定、发现闭包与调用链；**`app`、`dataset`** 本节不展开业务改造（仅列「暂不讨论」）。

---

## 二、产品愿景（已定方向摘要）

1. **五类资源开放平台**  
   统一登记、审核、上架、目录与消费出口；当前重点进化 **`agent`、`mcp`、Hosted `skill`** 三类的 **组合关系**。

2. **Skill 定义**  
   **`skill` = Hosted 提示词 / 推理能力**：平台保存提示模板与参数约定，在受控路径内 **代调大模型** 产出结果；可与 **技能包 zip（历史形态）** 以 `execution_mode` 区分。

3. **绑定关系（注册时配置）**  
   - **Agent 注册**：可绑定若干 **MCP**（`agent_depends_mcp`）。  
   - **MCP 注册**：可绑定若干 **Hosted Skill**（`mcp_depends_skill`，语义 **前置**：规范化入参 / 洗数据，再进入 MCP `tools/call`）。

4. **闭包与「暴露」**  
   - 绑定图在 **能力发现** 上采用 **对称闭包**（无向展开）：详情 `include=closure|bindings` 返回 **`bindingClosure`**。

5. **授权模型**  
   - **资源级 Grant（`t_resource_invoke_grant`）对网关 invoke 已下线**；以 **API Key**、**scope**、资源 **生命周期（published 等）**、RBAC 目录读为准。真值见 `ResourceInvokeGrantService`。

6. **外部门户集成**  
   继续以 **`POST …/invoke`**（及 MCP **`invoke-stream`**）为主；可选 **聚合工具列表**（`GET /catalog/capabilities/tools`）。

---

## 三、当前实现快照（后端，截至本仓库迭代）

- **网关**：`UnifiedGatewayServiceImpl` — `invoke` / `invoke-stream`；Hosted skill 分支、`applyMcpPreSkillChain`、`aggregatedCapabilityTools`。
- **关系**：`t_resource_relation` — `agent_depends_mcp`、`mcp_depends_skill`（及历史类型）；`ResourceRegistryServiceImpl.syncAllBindings`。
- **Skill 扩展**：`t_resource_skill_ext.execution_mode` 与 hosted 列（见 `sql/incremental/V34__hosted_skill_and_bindings.sql`）。

---

## 四、前端改造要点（联调清单）

- **资源注册**：Agent 多选 MCP；MCP 多选前置 Skill；Skill 表单区分 pack / hosted（字段见 `ResourceUpsertRequest`）。
- **市场 / 详情**：`include=closure|bindings` 展示 **绑定闭包**；可选调用聚合工具接口。

（前端工程不在本仓库时，以上以 `docs/frontend-alignment-handbook.md` 与 Swagger 为准。）

---

## 五、分阶段实施与验收

| 阶段 | 内容 | 状态（本仓库） |
|------|------|----------------|
| 0 | 规格冻结 | [platform-transformation-spec-freeze.md](platform-transformation-spec-freeze.md)（本目录） |
| 1 | DB + 注册读写 + resolve 闭包 | V34 + Registry + `bindingClosure` |
| 2 | Hosted Skill invoke | `HostedSkillExecutionService` + `invokeHostedSkill` |
| 3 | MCP 前置管线 | `applyMcpPreSkillChain` + 日志 |
| 4 | （可选）聚合 tools BFF | `GET /catalog/capabilities/tools`（及 SDK 同路径） |
| 5 | （可选）托管 Agent | 未在本迭代 |

**验收摘要**：注册绑定后闭包正确；`invoke(mcp)` 前置链可观测；hosted `invoke(skill)` 与 scope 一致；文档与 `PRODUCT_DEFINITION` 一致。

---

## 六、风险与 Non-goals

- **风险**：闭包环、工具名冲突、提示注入、模型成本（前置 Skill 每 MCP 调用多一次 LLM）。  
- **Non-goals**：`app`/`dataset` 业务大改；替换 MCP 协议。

---

*版本：2026-04-08；与代码同步更新。*
