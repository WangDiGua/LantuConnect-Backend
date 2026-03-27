# 旧接口物理删除清单（执行中）

## 说明

本清单用于第二阶段物理删除前审批。  
当前系统已处于第一阶段（废弃标记+禁写）。

## 当前执行进度

- 已删除：A（Agent）、B（Skill）、C（MCP）、D（App）、E（Dataset）、F（Provider）、G（Category）对应控制器
- 当前状态：本清单范围内控制器已全部完成物理删除

## A. Agent 旧接口（`/agents/**`）

- `GET /agents`
- `POST /agents`
- `GET /agents/{id}`
- `POST /agents/{id}/test`
- `PUT /agents/{id}`
- `DELETE /agents/{id}`

- `GET /agents/{agentId}/versions`
- `POST /agents/{agentId}/versions`
- `POST /versions/{versionId}/publish`
- `POST /versions/{versionId}/rollback`

对应控制器（已删除）：

- `com.lantu.connect.agent.controller.AgentController`
- `com.lantu.connect.agent.controller.AgentVersionController`

## B. V1 Skill 接口（`/v1/skills/**`）

- `GET /v1/skills`
- `POST /v1/skills`
- `GET /v1/skills/{id}`
- `PUT /v1/skills/{id}`
- `DELETE /v1/skills/{id}`
- `POST /v1/skills/{id}/invoke`

对应控制器（已删除）：

- `com.lantu.connect.skill.controller.SkillController`

## C. V1 MCP 接口（`/v1/mcp-servers/**`）

- `GET /v1/mcp-servers`
- `POST /v1/mcp-servers`
- `GET /v1/mcp-servers/{id}`
- `PUT /v1/mcp-servers/{id}`
- `DELETE /v1/mcp-servers/{id}`

对应控制器（已删除）：

- `com.lantu.connect.mcp.controller.McpServerController`

## D. V1 应用接口（`/v1/apps/**`）

- `GET /v1/apps`
- `POST /v1/apps`
- `GET /v1/apps/{id}`
- `PUT /v1/apps/{id}`
- `DELETE /v1/apps/{id}`

对应控制器（已删除）：

- `com.lantu.connect.app.controller.AppController`

## E. V1 数据集接口（`/v1/datasets/**`）

- `POST /v1/datasets`
- `PUT /v1/datasets/{id}`
- `DELETE /v1/datasets/{id}`
- `GET /v1/datasets/{id}`
- `GET /v1/datasets`
- `POST /v1/datasets/{id}/apply`

对应控制器（已删除）：

- `com.lantu.connect.dataset.controller.DatasetController`

## F. V1 Provider 接口（`/v1/providers/**`）

- `POST /v1/providers`
- `PUT /v1/providers/{id}`
- `DELETE /v1/providers/{id}`
- `GET /v1/providers/{id}`
- `GET /v1/providers`

对应控制器（已删除）：

- `com.lantu.connect.dataset.controller.ProviderController`

## G. V1 Category 接口（`/v1/categories/**`）

- `POST /v1/categories`
- `PUT /v1/categories/{id}`
- `DELETE /v1/categories/{id}`
- `GET /v1/categories`

对应控制器：

- `com.lantu.connect.dataset.controller.CategoryController`

## 审批项

- 依赖方通知是否完成
- 替代接口联调是否通过
- 最近 7 天调用量是否为 0 或可接受
- 回滚开关是否保留
- 删除窗口是否确认
