# 平台改造 — 规格冻结（Stage 0）

本文档锁定实现与联调所需的最小语义，后续迭代变更须在此更新版本记录。

## 1. 关系类型（`t_resource_relation.relation_type`）

| relation_type | from | to | 语义 |
|---------------|------|-----|------|
| `agent_depends_skill` | agent | skill | Agent 关联的技能包/托管技能（历史兼容） |
| `agent_depends_mcp` | agent | mcp | Agent 绑定 MCP；发现闭包自动并入 |
| `mcp_depends_skill` | mcp | skill | MCP 前置 Hosted Skill；`invoke(mcp)` 时可先执行归一化 |

## 2. 对称闭包（发现层）

- 绑定存储为有向边；**对调用方暴露的有效集合**按 **无向图** 在入口资源上取 **连通分量**（等价于沿正反方向遍历所有邻接）。
- **去重**：同一 `(resource_type, id)` 只出现一次。
- **环**：允许；遍历需 **visited** 集合防死循环。

## 3. Skill 执行模式（`t_resource_skill_ext.execution_mode`）

| 值 | 含义 |
|----|------|
| `pack` | 技能包（artifact 下载），**禁止** `invoke`（与现网一致） |
| `hosted` | 平台托管提示词推理，**允许** `invoke`；需配置 `hosted_system_prompt`、默认模型等 |

`skill_type`：托管技能建议使用 `hosted_v1`；包模式保持 `anthropic_v1` / `folder_v1` 等。

## 4. MCP 前置 Skill 失败策略（冻结）

- 若 MCP 绑定了前置 Hosted Skill，网关在进入上游 MCP 前依次执行（当前实现为 **顺序执行，后者输入为前者输出** 的 JSON 文本链路；**仅第一阶段** 以原始 payload 为输入）。
- **LLM 返回非合法 JSON** 或 **schema 解析失败**：返回业务错误 `PARAM_ERROR`（或专用码），**不** 静默直通上游（避免脏参数打穿）。
- 后续可配置项（未实现）：`pre_skill_on_error: fail_fast | passthrough`。

## 5. Hosted Skill 模型调用

- 使用 **OpenAI 兼容** `POST {baseUrl}/v1/chat/completions`（可配置）。
- 密钥与 base URL 来自 `application.yml` / 环境变量（见主配置），**非** 每资源密钥（后续可扩展）。

## 6. API Key 与资源级 Grant

- 资源级 Grant **已下线**；以 **API Key scope + published** 为准。

---
*Version: 2026-04-08*
