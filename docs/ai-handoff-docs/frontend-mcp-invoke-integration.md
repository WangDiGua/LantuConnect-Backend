# 前端对接：统一网关 MCP 调用（HTTP Streamable / SSE 响应 / WebSocket）

本文说明前端（或开放平台控制台）如何在**不直连上游 MCP**的前提下，通过兰智通统一网关完成 MCP 类资源的试用与集成。后端已对三类接入方式做了协议层适配，前端仍只调用现有的 **`POST {context}/invoke`**（SDK：`POST {context}/sdk/v1/invoke`，默认 `context=/regis`），差异体现在**资源注册时的 endpoint、`auth_config`（spec）与请求体 `payload`**。

## 1. 统一调用接口（不变）

| 项 | 说明 |
| --- | --- |
| URL | `POST {baseUrl}/invoke`（`baseUrl` 含 `server.servlet.context-path`，默认 `…/regis`；SDK：`POST {baseUrl}/sdk/v1/invoke`） |
| 鉴权 | 必填请求头 **`X-Api-Key`**（与用户拥有的资源 scope 一致） |
| 可选头 | `X-Trace-Id` / `X-Request-Id`：贯穿一次 JSON-RPC 的 `id`，便于与上游日志对齐；不传则服务端生成 UUID |
| 可选头 | `X-User-Id`：若网关策略需要用户维度治理时携带 |

**请求体**（`InvokeRequest`）：

```json
{
  "resourceType": "agent",
  "resourceId": "123",
  "version": null,
  "timeoutSec": 60,
  "payload": {
    "method": "initialize",
    "params": {}
  }
}
```

**严格上游（如魔搭 streamable_http）**：

- `initialize` 须使用 MCP 规范完整 `params`（`protocolVersion`、`capabilities`、`clientInfo`），勿传空对象。
- `tools/list`、`notifications/initialized` 等**无参**调用：网关对「空 `params`」会改为**省略 JSON-RPC 的 `params` 字段**（不再发 `"params":{}`）；`notifications/*` 会**省略 `id`**（按 JSON-RPC Notification）。否则部分上游统一返回 `-32602 Invalid request parameters`。

**响应体**（`InvokeResponse` 外层为统一 `R<T>` 包装时以项目现有约定为准）：

- `statusCode`：上游 HTTP 状态（WebSocket 路径在成功取到文本帧后固定为 `200`）。
- `body`：**字符串**，内容为上游返回的 JSON 文本（见下）。
- `latencyMs`、`traceId`、`status` 等与其他协议一致。

前端解析 **`data.body`**（或 unwrap 后的 `body`）为 JSON 即可展示或继续作为下一轮 `payload` 的输入参考。

### 1.1 `agent` / `activeSkillIds` 与 `_lantu.bindingExpansion`

- 当 **`resourceType=agent`** 且登记 **`agent_depends_mcp`** 时，网关会在**转发到上游 Agent** 之前，对涉及的 MCP 执行 `tools/list` 聚合（与 `GET /catalog/capabilities/tools` 同源逻辑），并将结果写入本次 **`payload`** 内的保留键 **`_lantu.bindingExpansion`**（含 `entry`、`openAiTools`、`routes`、`warnings`）。上游 Agent 可选择是否消费；**网关不自动代打 `tools/call`**。
- 调用方还可在 **`payload.activeSkillIds`** 或 **`payload._lantu.activeSkillIds`** 中声明 Skill 资源 id；当开启 **`merge-active-skill-mcps`** 时，网关会把这些 Skill 上的 **`skill_depends_mcp`** 一并合并进 `_lantu.bindingExpansion`。Skill 自身仍是 Context 资源，**不作为**统一网关 `invoke` 目标。
- **仅 invoke `mcp`** 时 **不会**反向注入 Agent；MCP 仍按自身 `resolve` 结果直接调用。
- 调用方 Key 须对展开涉及的每个 MCP 具备 **invoke** scope，否则缺权 MCP 列入 `warnings`。可用 `lantu.gateway.binding-expansion` 关闭或分项关闭（`enabled` / `agent` / `merge-active-skill-mcps`）。

## 2. 三种 MCP 相关传输与前端含义

### 2.1 HTTP / Streamable HTTP（含 SSE 正文）

- **资源 `endpoint`**：`https://...` 常规 MCP HTTP 地址。
- **行为**：网关使用 `java.net.http` 发起**单次**请求，读取**完整**响应体；若 `Content-Type` 为 `text/event-stream` 或正文像 SSE（含 `event:` / `data:`），服务端会解析 SSE，**尽量合并/挑选**与当前 `traceId` 匹配的 `data:` JSON 行，再放进 `InvokeResponse.body`。
- **`Mcp-Session-Id`**：若上游在响应头返回 `Mcp-Session-Id`，网关会按 **`API Key 主键 id + endpoint`** 存入 Redis，并在**同一密钥**的下一次该 endpoint 调用请求头中自动带上 `Mcp-Session-Id`。TTL 由配置 `lantu.integration.mcp-session-ttl-minutes` 控制（默认 45 分钟，服务端 clamp 在 5～1440 分钟）。若上游先于本地 TTL 使会话失效（典型 HTTP 401 + body 含 `SessionExpired`），网关会**删除该缓存并自动用同一条 JSON-RPC 重发一次**（第二次请求不再带过期 `Mcp-Session-Id`），用户一般无需换 Key。
- **前端要点**：
  - 多轮 MCP（如先 `initialize` 再 `tools/list` 再 `tools/call`）在 UI 上仍是**多次**调 ` /catalog/invoke`，**每次带好同一个 `X-Api-Key`** 即可复用会话；无需自己传 `Mcp-Session-Id`。
  - 建议**复用同一 `X-Trace-Id`** 贯穿一轮调试，便于在 `body` 中与 JSON-RPC `id` 对齐（服务端 SSE 解析会优先匹配带 `"id":"<traceId>"` 的 `data:` 行）。
  - 若上游在长连接场景下持续推送多事件，网关仍是一次 HTTP **读至连接结束**再解析；前端应接受「合并后的一条 JSON」或「最后一条 `data:` JSON」的语义，以实际 `body` 为准。

### 2.2 「纯 SSE 长连接」语义在网关上的边界

部分 MCP 实现要求**同一条 TCP 连接**上顺序完成 `initialize`、`notifications/initialized`、再 `tools/list`。当前网关模型是 **无状态的「一次 invoke = 一次上游 HTTP 请求-响应」**，无法把浏览器 SSE 长连接原样桥接到上游。

- **可行用法**：上游若支持 **Streamable HTTP**（每次 POST 可返回 SSE 块但仍在一次响应内结束），或每步可独立 HTTP 调用，则前端按多轮 `/invoke` 即可。
- **不可行或需上游改造**：必须单连接多 method 且不关闭流的实现，需要上游提供 **HTTP 分步 API** 或由平台增加专用适配；前端侧无需改路径，但要避免假设「浏览器 EventSource 直连资源 URL」。

### 2.3 WebSocket（`ws` / `wss`）

- **资源 `endpoint`**：必须以 **`ws://` 或 `wss://`** 开头。
- **`auth_config`（spec）**：若 endpoint 不是 `ws`/`wss`，但 `spec.transport === "websocket"`，后端会要求管理员把 endpoint 改成 `ws`/`wss`，否则会返回参数错误（避免误把 HTTPS URL 当 WS 用）。
- **行为**：网关建立 WebSocket，发送与 HTTP 路径相同的 **JSON-RPC 文本**（一条），在收到**上游标记结束的最后一条文本帧**后关闭连接，将累积的字符串作为 `body` 返回。
- **前端要点**：
  - 前端**仍然只调 HTTP** `/catalog/invoke`，不要在浏览器里直连 `wss://` 上游（除非走单独设计的安全通道）。
  - 若上游分多帧返回且最后一帧 `fin` 行为与 JDK HttpClient WebSocket 约定不一致，可能出现超时或 `body` 不完整——需按实际上游协议拉长 `timeoutSec` 或与后端联调。

## 3. `payload` 与 `auth_config` 的优先级（MCP）

- JSON-RPC **`method`**：优先取 **`payload.method`**；未传时才回退到 `auth_config` 中的 `method`。
- **`params`**：优先使用 **`payload.params`**（对象）；若无 `params` 键，则把 `payload` 去掉 `method` 后的字段整体当作 `params`。

典型序列（示例仅供字段说明，`params` 以内真实 MCP 为准）：

1. `initialize`  
2. `notifications/initialized`（若上游需要且可在独立 POST 中完成）  
3. `tools/list`  
4. `tools/call`（`params` 内含 `name`、arguments 等）

工具名称必须通过 `tools/list` 取得，不要用注册表里写死的展示名替代 `name`。

## 4. 管理端表单建议（资源注册 / 详情页）

- **协议**：`invokeType` / `auth_config.protocol` 为 **`mcp`**（与后端 `McpJsonRpcProtocolInvoker` 一致）。
- **Endpoint**：
  - HTTP：`https://...`
  - WS：`wss://...`
- **可选 spec 字段**：`transport: "websocket"`（用于显式声明；与 `wss` URL 二选一或同时使用，以代码校验为准）。
- **Playground（试用）**：
  - 下拉选择 JSON-RPC `method`，或高级模式直接编辑 JSON `payload`。
  - 固定显示当前 `X-Trace-Id`（可一键重置），方便用户对照响应 `id`。
  - 对 MCP 资源展示简短说明：「多轮请连续多次点击调用，保持同一 API Key；会话头由网关维护」。

## 5. 配置与运维（供前端/联调同学知悉）

| 配置项 | 作用 |
| --- | --- |
| `lantu.integration.mcp-http-accept` | MCP HTTP 请求的 `Accept` 头，默认包含 `application/json` 与 `text/event-stream`。 |
| `lantu.integration.mcp-session-ttl-minutes` | `Mcp-Session-Id` 在 Redis 中的存活时间。 |
| `lantu.integration.mcp-invoke-retries` | MCP HTTP 失败重试次数（每次重试前重新从 Redis 读取会话头）。 |

Redis 不可用时，会话无法复用，多轮 MCP 可能在上游校验 session 处失败——环境检查清单中应包含 Redis。

## 6. 错误与调试

- **401**：缺少或无效 `X-Api-Key`。
- **参数类错误**：WebSocket 声明与 URL 不一致、endpoint 为空、`invokeType` 非平台支持协议等，以服务端 `BusinessException` 消息为准。
- **上游 4xx/5xx**：`InvokeResponse.status` 为 `error`，`statusCode` 为上游状态；`body` 可能为空，错误信息在运维日志中。

---

*文档版本与后端实现：`McpJsonRpcProtocolInvoker`、`McpStreamSessionStore`、`McpSsePayloadParser`、`ProtocolInvokeContext`（2026-03-26 起）。*
