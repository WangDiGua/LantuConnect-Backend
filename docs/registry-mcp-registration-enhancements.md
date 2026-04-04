# 注册中心（纯登记）— MCP 登记体验增强计划与落地

**产品定位**：本平台为**资源注册与目录**，**不托管、不代运维**用户的 MCP 进程。下列能力均为「登记质检 / 降摩擦」，不涉及替用户运行服务。

## 计划清单（勾选 = 本迭代已落地）

- [x] **产品说明**：注册页明确「须由登记方自行暴露可访问的 URL/ws；本机 stdio 需自备 HTTP/ws 边车」。
- [x] **连通性探测**：登记前对已填 endpoint + 鉴权发起一次 **JSON-RPC `initialize`** 探测（短超时）；成功/失败文案区分网络、鉴权、上游拒绝等（探测走平台出站策略，与正式 invoke 一致）。
- [x] **配置粘贴导入**：支持粘贴常见 **`mcpServers`** / **`mcp.json`** 片段，自动提取 **`url` + `headers`** 填入表单；若为 **`command`/`args` 仅 stdio** 则提示无法直接登记并说明边车要求。
- [ ] **OAuth 浏览器授权**：非本迭代；需产品单独立项（Client Credentials 已支持）。
- [ ] **健康度轮询 / 目录徽章**：可选后续；与 observability 模块联动。
- [ ] **多环境 URL**：与版本管理产品策略对齐后再做。

## 接口约定

- `POST /resource-center/resources/mcp/connectivity-probe`  
  Body：`{ "endpoint", "authType"?, "authConfig"?, "transport"? }`  
  响应：`{ "ok", "statusCode", "latencyMs", "message", "bodyPreview"? }`  

须登录（`X-User-Id`），与现有资源中心接口一致。

## 说明

- `http_json` 与 `http_sse` 在元数据上均映射为 `transport=http`；实际兼容由网关按上游响应解析，与 [`docs/ai-handoff-docs/frontend-mcp-invoke-integration.md`](ai-handoff-docs/frontend-mcp-invoke-integration.md) 一致。
