# 前端资源注册闭环操作手册

本手册用于前端直接对接统一资源注册中心，覆盖 MCP/Skill/Agent/App/Dataset 从创建到发布、再到版本切换的完整流程。

## 1. 统一入口

- 基础路径：`/resource-center/resources`
- 认证：所有接口需要登录态（`Authorization: Bearer <token>`），后端会注入 `X-User-Id`
- 统一返回：`{ code, message, data }`

## 2. 标准闭环流程

1. 创建资源：`POST /resource-center/resources`
2. 更新资源：`PUT /resource-center/resources/{id}`（可选）
3. 提审：`POST /resource-center/resources/{id}/submit`
4. 审核通过：`POST /audit/resources/{auditId}/approve`（管理员）
5. 审核通过后即上线：无单独发布步骤（管理员执行 `POST /audit/resources/{auditId}/approve`）
6. 下线：`POST /resource-center/resources/{id}/deprecate`（资源拥有者或管理员）

## 3. 创建请求体（按类型）

## 3.1 MCP

```json
{
  "resourceType": "mcp",
  "resourceCode": "campus-kb-mcp",
  "displayName": "校园知识库 MCP",
  "description": "用于知识检索和问答",
  "sourceType": "internal",
  "endpoint": "http://localhost:9000/mcp",
  "protocol": "mcp",
  "authType": "none",
  "authConfig": {
    "method": "tools/call"
  }
}
```

## 3.2 Skill

```json
{
  "resourceType": "skill",
  "resourceCode": "text-translate",
  "displayName": "文本翻译",
  "description": "中英互译",
  "sourceType": "cloud",
  "skillType": "http_api",
  "mode": "TOOL",
  "spec": {
    "url": "https://api.example.com/translate",
    "timeout": 30
  },
  "parametersSchema": {
    "type": "object",
    "properties": {
      "text": { "type": "string" },
      "to": { "type": "string" }
    }
  }
}
```

## 3.3 Agent

```json
{
  "resourceType": "agent",
  "resourceCode": "research-agent",
  "displayName": "研究助手",
  "description": "文献检索和摘要",
  "sourceType": "internal",
  "agentType": "http_api",
  "mode": "SUBAGENT",
  "spec": {
    "url": "https://api.example.com/agent/research",
    "timeout": 60
  },
  "maxConcurrency": 10,
  "systemPrompt": "你是研究助理"
}
```

## 3.4 App

```json
{
  "resourceType": "app",
  "resourceCode": "seat-booking",
  "displayName": "座位预约",
  "description": "图书馆座位预约",
  "sourceType": "internal",
  "appUrl": "https://app.example.com/seat",
  "embedType": "iframe"
}
```

## 3.5 Dataset

```json
{
  "resourceType": "dataset",
  "resourceCode": "exam-stats-2026",
  "displayName": "考试统计数据 2026",
  "description": "脱敏数据集",
  "sourceType": "department",
  "dataType": "structured",
  "format": "csv",
  "recordCount": 120000,
  "fileSize": 104857600,
  "tags": ["成绩", "统计"]
}
```

## 4. 版本管理

- 创建版本：`POST /resource-center/resources/{id}/versions`

```json
{
  "version": "v2",
  "makeCurrent": true
}
```

- 切换版本：`POST /resource-center/resources/{id}/versions/{version}/switch`
- 查询版本：`GET /resource-center/resources/{id}/versions`

## 5. 前端联调建议

- 资源管理页使用 `GET /resource-center/resources/mine` 拉取“我的资源”
- 资源状态按钮严格按状态机渲染（避免非法点击）
- 提审成功后跳转审核详情页，展示 `pending_review`
- MCP 创建页提交前做 URL 与 `protocol` 基础校验，避免后端 400

## 6. 表结构说明（本轮）

- 本轮能力使用既有表：`t_resource`、`t_resource_agent_ext`、`t_resource_skill_ext`、`t_resource_mcp_ext`、`t_resource_app_ext`、`t_resource_dataset_ext`、`t_resource_version`、`t_audit_item`
- 本轮无新增或修改表结构
