# RobotFactory / AI 门户无缝对接改造说明

## 0. 截至 2026-04-22 的已落地实现更新

本节用于覆盖本文中已经过时的计划性描述。若本文后续章节与本节冲突，**以本节和当前代码实现为准**。

### 0.1 当前已落地范围

当前项目已经完成第一阶段的核心适配能力，包含：

- 后端独立适配模块：`com.lantu.connect.compat.robotfactory.*`
- 前端超管页面：`平台配置 -> 软件工厂适配`
- 软件工厂连接配置的前端维护、测试连接、自动健康检查
- MCP 资源投影管理
- 学校与 `corp_id` 映射管理
- 同步日志查询
- 外部库 `genie_external_agent` 的写入、更新、删除
- 软件工厂兼容 MCP SSE 接口
- 来源 IP 白名单校验

第一阶段**仍未接管**的软件工厂能力：

- `genie_agent_client_scope`
- `genie_agent_permission`
- 软件工厂侧“绑定机器人”
- 软件工厂侧“修改权限”
- 软件工厂侧缓存自动刷新

### 0.2 当前本地表与配置实际落地情况

当前代码与数据库已落地以下本地表：

- `t_robotfactory_projection`
- `t_robotfactory_corp_mapping`
- `t_robotfactory_sync_log`

当前软件工厂连接与运行配置**不写在 YAML 中**，统一存储在本平台数据库：

- 表：`t_system_param`
- 键：`robot_factory_adapter_config`

当前该 JSON 配置实际承载的字段包括：

- `dbUrl`
- `dbUsername`
- `dbPassword`
- `dbDriverClassName`
- `publicBaseUrl`
- `allowedIps`
- `sessionIdleMinutes`
- `sessionMaxLifetimeMinutes`
- `invokeTimeoutSeconds`

这意味着：

- 软件工厂数据库连接已支持在前端页面中修改
- 可以在前端直接测试连接
- 后端会定时自动检查连接健康状态
- 后续任何环境切换都应优先改系统配置，不应再回退到写死配置文件

### 0.3 当前健康检查真实口径

当前健康检查不再只判断“数据库通不通”，而是同时判断以下三件事：

1. 软件工厂数据库是否可达
2. 当前连接的数据库中是否存在 `genie_external_agent`
3. 是否已配置 `publicBaseUrl`，即“对外访问地址”

当前健康状态对象已包含：

- `configured`
- `databaseReachable`
- `externalTableReady`
- `publicBaseUrlReady`
- `status`
- `message`
- `checkedAt`

当前页面“可同步”的判断口径为：

- 数据库可达
- `genie_external_agent` 已检测到
- `publicBaseUrl` 已配置

也就是说，**即使数据库已连通、表也存在，只要没有配置对外访问地址，当前实现也不会视为“可同步”**。

### 0.4 `spec_json` 的当前真实生成与限制

当前投影 `spec_json` 的生成方式如下：

- 优先使用投影级 `specJsonOverride`
- 否则按系统配置中的 `publicBaseUrl` + `server.servlet.context-path` + 固定兼容路径自动生成

当前兼容地址固定为：

- `GET /regis/compat/robot-factory/mcp/{projectionCode}/sse`
- `POST /regis/compat/robot-factory/mcp/{projectionCode}/message?session_id=...`

当前代码已经补上一个关键限制：

- 同步到软件工厂前，`spec_json.url` **必须是可直连的绝对地址**
- 仅允许 `http://` 或 `https://` 开头
- 若仍是 `/regis/...` 这类相对路径，则直接拒绝同步

因此当前落地口径是：

- `publicBaseUrl` 未配置时，不允许继续把相对路径同步到软件工厂
- 之前已经同步过相对路径的投影，需要在补全 `publicBaseUrl` 后重新执行一次同步

### 0.5 当前 `genie_external_agent` 同步口径

当前只同步软件工厂外部表：

- `genie_external_agent`

当前不会写入：

- `genie_agent_client_scope`
- `genie_agent_permission`

当前 `genie_external_agent` 的同步行为为：

- 按 `agent_name` 查询是否已存在
- 存在则 `UPDATE`
- 不存在则 `INSERT`
- 成功后回写 `external_agent_id`

当前删除口径已经按已确认方案落地为：

- **物理删除**软件工厂侧 `genie_external_agent` 记录

不是旧文档中某些章节提到的：

- `yn = 0`
- `hidden = 1`

因此若本文后续还出现“下线时优先改 `yn=0`”之类描述，应视为旧计划，**以物理删除为准**。

### 0.6 当前资源范围与同步触发口径

当前投影与自动同步的实际范围为：

- 仅支持 `published` 的 `MCP` 资源

当前实现具备以下同步入口：

- 手动创建投影后手动同步
- 投影开启 `autoSyncEnabled` 后，资源发布 / 更新 / 下线时自动触发对应同步逻辑

当前自动同步默认值为：

- `autoSyncEnabled = false`

即第一阶段仍是：

- 手动 + 自动并存
- 但自动同步需要投影级显式开启

### 0.7 当前前端页面真实交互

当前前端页面已经不是“表单和列表整页平铺”的旧形态，现已调整为：

- `投影列表` 页：仅保留筛选和列表，新建/编辑统一进入弹窗
- `学校 Corp 映射` 页：仅保留说明和列表，新建/编辑统一进入弹窗
- `同步日志` 页：独立查询与查看
- `适配设置` 页：数据库连接、对外地址、白名单、连接导入、健康状态统一维护

当前页面还已支持：

- 导入 Navicat `.ncx` 连接文件
- 尝试解析并回填数据库连接信息
- 密码明文显示/隐藏切换

### 0.8 当前同步失败的关键阻断条件

在当前代码里，以下情况会直接阻止同步：

- `scopeMode = school` 但未找到有效的 `corp_id` 映射
- `spec_json` 为空
- `spec_json.url` 为空
- `spec_json.url` 不是绝对地址
- 软件工厂数据库不可达
- 当前数据库中不存在 `genie_external_agent`
- 未配置 `publicBaseUrl`

因此当前实际实施顺序应为：

1. 在“适配设置”中配置正确的 `dbUrl`
2. 确认该 `dbUrl` 指向的软件工厂数据库中存在 `genie_external_agent`
3. 配置可被软件工厂直连访问的 `publicBaseUrl`
4. 配置来源 IP 白名单
5. 维护学校与 `corp_id` 映射
6. 创建投影并同步

### 0.9 当前接口与模块落地情况

当前后端已落地的管理端接口包括：

- `GET /regis/system-config/robot-factory/settings`
- `PUT /regis/system-config/robot-factory/settings`
- `POST /regis/system-config/robot-factory/settings/test-connection`
- `GET /regis/system-config/robot-factory/settings/health`
- `GET /regis/system-config/robot-factory/corp-mappings`
- `POST /regis/system-config/robot-factory/corp-mappings`
- `PUT /regis/system-config/robot-factory/corp-mappings/{id}`
- `GET /regis/system-config/robot-factory/available-resources`
- `GET /regis/system-config/robot-factory/projections`
- `GET /regis/system-config/robot-factory/projections/{id}`
- `POST /regis/system-config/robot-factory/projections`
- `PUT /regis/system-config/robot-factory/projections/{id}`
- `POST /regis/system-config/robot-factory/projections/{id}/sync`
- `DELETE /regis/system-config/robot-factory/projections/{id}/external`
- `POST /regis/system-config/robot-factory/projections/{id}/auto-sync`
- `GET /regis/system-config/robot-factory/sync-logs`

当前兼容运行时接口为：

- `GET /regis/compat/robot-factory/mcp/{projectionCode}/sse`
- `POST /regis/compat/robot-factory/mcp/{projectionCode}/message?session_id=...`

当前后端核心模块包括：

- `RobotFactoryAdminController`
- `RobotFactoryMcpCompatController`
- `RobotFactorySettingsService`
- `RobotFactoryProjectionService`
- `RobotFactorySyncService`
- `RobotFactoryCompatService`
- `RobotFactorySessionService`
- `RobotFactoryWhitelistService`

### 0.10 当前仍需注意的现实约束

虽然第一阶段已落地，但以下约束依然成立：

- 软件工厂侧仍需人工绑定机器人
- 软件工厂侧仍需人工配置权限
- 软件工厂缓存仍需对方人工刷新
- 当前 `genie_external_agent` 仍按既定字段写入，后续若对方表结构变化，需继续补“字段契约检查 / 映射配置化”

建议后续第二阶段优先补齐：

- `genie_external_agent` 关键字段结构检查
- 字段映射配置化
- 同步失败的更细粒度诊断与提示
- 已存在脏 `spec_json` 数据的批量重同步工具

## 1. 背景

当前 `LantuConnect-Backend` 已经具备自己的资源注册、发布、授权、统一调用、调用日志、使用统计、健康检测、熔断降级、Trace 追踪等能力，平台本身是一个面向多类调用方的资源注册与调用平台，不仅服务公司内部 AI 门户，还需要继续服务师生端和第三方平台。

公司现有 AI 门户并不是直接消费本平台现有的数据模型和接口，而是沿用其既有的“智能体/能力注册”模型与调用流程。用户提供的信息表明，对方平台当前主要依赖以下表及协议：

- `genie_external_agent`
- `genie_agent_client_scope`
- `genie_agent_permission`
- MCP SSE 协议：`GET /sse` + `POST /message?session_id=...`

同时，对方已经有运行中的工具工程：

- `D:\LantuConnect\robotagents`

从该工程可以看出，对方门户实际依赖的是一套“注册表 + 绑定/权限 + MCP SSE 调用 + 缓存刷新”的组合机制，而不是简单地调用一个 HTTP API。

因此，本次改造的目标不是把蓝图连接平台整体改造成对方的平台模型，而是在不破坏本平台既有定位的前提下，新增一层 **RobotFactory / AI 门户兼容适配层**，实现对方平台对本平台资源的无缝接入。

## 2. 改造目标

本次改造要达到的目标如下：

1. 对方 AI 门户可以继续走自己现有的能力注册、机器人绑定、权限控制、门户展示、MCP 调用流程。
2. 本平台继续保持自身作为“资源主平台”的定位，不因对接某一个渠道而重构核心数据模型。
3. 对方通过兼容层调用本平台资源时，尽可能保留本平台现有的：
   - 调用日志
   - 使用统计
   - Trace 追踪
   - 健康状态
   - 熔断降级
   - 统一治理能力
4. 对接方式要兼容未来的多渠道扩展，而不是只为 RobotFactory 做一次性特化。

### 2.1 当前确定的第一阶段范围

截至当前沟通结果，**第一阶段只做“资源注册投影 + 运行时兼容接入”**，不做软件工厂侧的全量治理接管。

第一阶段由本平台负责：

- 选择哪些资源需要投影到软件工厂
- 将资源写入对方 `genie_external_agent`
- 为投影资源提供 MCP SSE 兼容入口
- 资源更新、下线时同步更新或失效对应注册记录

第一阶段暂不由本平台负责：

- 自动写入 `genie_agent_client_scope`
- 自动写入 `genie_agent_permission`
- 软件工厂侧的机器人绑定
- 软件工厂侧的权限配置

这两块仍由软件工厂管理员在其现有页面中人工完成：

- 绑定机器人
- 修改权限

因此，第一阶段的最小闭环是：

1. 本平台资源投影
2. 写入 `genie_external_agent`
3. 软件工厂侧人工绑定机器人
4. 软件工厂侧人工设权限
5. 软件工厂通过本平台兼容 MCP 地址调用

## 3. 现状研判

### 3.1 本平台现状

当前后端已经存在较完整的统一调用链，相关核心代码位于：

- `src/main/java/com/lantu/connect/gateway/service/impl/UnifiedGatewayServiceImpl.java`
- `src/main/java/com/lantu/connect/gateway/protocol/ProtocolInvokerRegistry.java`
- `src/main/java/com/lantu/connect/gateway/protocol/McpJsonRpcProtocolInvoker.java`
- `src/main/java/com/lantu/connect/gateway/protocol/ProtocolInvokeContext.java`

从现有实现可以确认：

- 平台已有统一的资源调用入口与解析逻辑
- 调用过程中会写入调用日志与使用记录
- 调用链已接入 Trace
- 调用链已接入健康检查与熔断相关治理
- MCP 协议调用已有基础能力

这意味着：**只要 RobotFactory 兼容层最终复用本平台统一调用链，而不是绕开它直接打下游，就能最大限度保留现有治理能力。**

### 3.2 对方平台现状

结合用户提供的表结构和 `robotagents` 项目代码，可以得出以下结论：

#### 3.2.1 对方平台的注册模型

`genie_external_agent` 并不只是一个展示表，而是对方门户识别“可注册能力”的核心注册表。它至少承载了以下信息：

- 能力唯一标识：`agent_name`
- 展示名称：`display_name`
- 描述：`description`
- 接入类型：`agent_type`
- 运行模式：`mode`
- 展示模板：`display_template`
- 协议配置：`spec_json`
- 调度、运行角色、交互方式：`runtime_role` / `interaction_mode` / `dispatch_mode`
- 历史统计：`quality_score` / `avg_latency_ms` / `success_rate` / `avg_token_cost` / `call_count`

#### 3.2.2 对方平台的绑定模型

`genie_agent_client_scope` 表明对方平台不是“注册后全局自动可用”，而是按 `client_code` 控制能力可用范围：

- `agent_id` 对应注册能力
- `client_code` 对应客户端 / 机器人标识
- `*` 表示全部客户端

这本质上是“能力对哪些机器人可见 / 可绑定”的模型。

结合对方现有资源注册页面也可进一步确认：

- 软件工厂已具备“绑定机器人”操作入口
- 这意味着其产品侧已经有现成的机器人挂载流程
- 该能力与本平台“API Key 绑定集成套餐后，只能调用套餐内资源”的约束方式有相似性
- 因此第一阶段无需由本平台重复建设这套绑定能力，只需把资源注册进去，后续绑定继续由对方页面完成

#### 3.2.3 对方平台的权限模型

`genie_agent_permission` 说明对方门户还存在第二层裁剪：

- 按权限组 `group_id`
- 按范围 `scope_type = global/corp/client`
- 按租户 `corp_id`
- 按客户端 `client_code`
- 允许/拒绝 `permission = allow/deny`

这意味着：即使一个能力已经注册，且对某个 `client_code` 可见，也可能因为权限组规则而不可调用。

结合对方现有资源注册页面也可进一步确认：

- 软件工厂已具备“修改权限”操作入口
- 说明权限设定已经是其既有产品能力，而非本次必须由本平台替代实现的能力
- 因此第一阶段不应把范围扩展到权限页面与权限同步接管，除非后续明确要求由本平台统一维护

#### 3.2.4 对方平台的运行协议

`D:\LantuConnect\robotagents` 项目中的 `AbstractMcpResource` 明确展示了对方工具运行时的 MCP SSE 模式：

- `GET /sse`
- 服务端通过 SSE 返回 `message?session_id=...`
- `POST /message?session_id=...`
- 支持 JSON-RPC 方法：
  - `initialize`
  - `notifications/initialized`
  - `tools/list`
  - `tools/call`

这说明对方 AI 门户期待接入方提供的是“它能识别的 MCP 工具服务”，而不是单纯的 REST API。

#### 3.2.5 对方平台存在缓存依赖

`robotagents` 中 `InternetSearchService` 显示其会从 Redis 键中读取外部能力缓存：

- `robot:agents:external_agent_all`

这说明：

- 对方注册数据可能不仅落库，还会有缓存副本
- 对接完成后，能力注册更新通常还需要触发缓存刷新或同步

#### 3.2.6 对方平台存在上下文注入

`AbstractMcpResource` 中可以看到对方会向工具注入部分运行上下文参数，例如：

- `_corp_id`
- `_upload_dir`
- `_server_address`
- `_user_name`

部分工具还依赖：

- `client_code`

因此，对接不能只考虑“能调用”，还需要考虑“调用时上下文是否兼容”。

## 4. 核心判断

### 4.1 不应该做的事

以下方向不建议采用：

1. 直接把本平台核心资源模型改造成 `genie_external_agent` 模型。
2. 让本平台所有资源、权限、绑定逻辑都服从 RobotFactory 的字段设计。
3. 让对方门户绕过本平台统一网关，直接调用资源底层地址。
4. 为了兼容对方而牺牲本平台对师生端和第三方平台的统一性。

原因如下：

- 本平台是多渠道资源主平台，不应被单一消费方的数据模型绑死。
- 对方模型主要解决的是“门户注册、机器人绑定、权限投影、调用协议兼容”，并不是本平台内部资源管理的完整替代品。
- 一旦绕开统一网关，本平台已有的统计、审计、熔断、Trace、治理能力会被破坏。

### 4.2 应该采用的方向

建议采用的方向是：

**以本平台资源中心为主模型，新增一个 RobotFactory 渠道兼容适配层。**

该兼容适配层负责：

1. 将本平台资源投影为 RobotFactory 可识别的能力记录。
2. 在后续阶段可扩展支持 RobotFactory 所需的客户端范围和权限范围。
3. 暴露 RobotFactory 所需的 MCP SSE 兼容接口。
4. 将对方的调用重新路由回本平台统一调用链。
5. 负责必要的同步、刷新与状态映射。

## 5. 建议的整体架构

建议把整体链路拆成两层：

### 5.1 管理面适配

管理面适配负责“让对方平台看到并接纳本平台资源”。

主要职责：

- 资源投影：把本平台已发布资源映射为渠道能力
- 显示配置映射：生成对方可识别的 `display_template` / `mode` / `agent_type`
- 在后续阶段可扩展 client 范围映射：维护对方所需的 `client_code` 可见范围
- 在后续阶段可扩展权限范围映射：维护对方所需的权限规则
- 同步到外部平台：第一阶段先写入 `genie_external_agent`
- 缓存刷新：通知对方刷新能力缓存

### 5.2 运行面适配

运行面适配负责“让对方平台可以像调用自己的能力一样调用本平台资源”。

主要职责：

- 提供符合对方预期的 MCP SSE 接口
- 为每个外部会话维护独立的 `session_id`
- 响应 `tools/list`
- 响应 `tools/call`
- 处理对方注入的上下文参数
- 将最终调用路由至本平台统一网关
- 保留本平台调用日志、Trace、统计、熔断能力

## 6. 建议新增的本地模型

不建议直接把 `genie_*` 三张表搬进本平台核心域，而建议在本平台内部新增“渠道投影模型”。

完整方案下，建议最少新增以下三类表。

### 6.1 渠道能力投影表

用途：表示“本平台某个资源被投影为 RobotFactory 能力后的渠道侧定义”。

建议字段方向：

- `id`
- `channel_code`，建议固定值如 `robot_factory`
- `resource_id`
- `resource_type`
- `agent_name`
- `display_name`
- `description`
- `display_template`
- `agent_type`
- `mode`
- `runtime_role`
- `interaction_mode`
- `dispatch_mode`
- `spec_json`
- `parameters_schema`
- `enabled`
- `sync_status`
- `sync_time`
- `external_agent_id`
- `create_time`
- `update_time`

对应关系上，基本可映射到对方的 `genie_external_agent`。

### 6.2 渠道客户端范围表

用途：表示“某个渠道能力对哪些 `client_code` 可见/可用”。

建议字段方向：

- `id`
- `channel_code`
- `projection_id`
- `client_code`
- `enabled`
- `sync_status`
- `sync_time`
- `create_time`

对应关系上，可映射到对方的 `genie_agent_client_scope`。

说明：

- **第一阶段可暂不落地此表**
- 机器人绑定仍由软件工厂管理员在其页面中人工维护
- 若后续需要本平台统一维护“绑定机器人”，再补此表与同步逻辑

### 6.3 渠道权限投影表

用途：表示“某个渠道能力在渠道侧的权限规则”。

建议字段方向：

- `id`
- `channel_code`
- `projection_id`
- `group_id`
- `scope_type`
- `corp_id`
- `client_code`
- `permission`
- `enabled`
- `sync_status`
- `sync_time`
- `create_time`
- `update_time`

对应关系上，可映射到对方的 `genie_agent_permission`。

说明：

- **第一阶段可暂不落地此表**
- 权限设定仍由软件工厂管理员在其页面中人工维护
- 若后续需要本平台接管软件工厂权限投影，再补此表与同步逻辑

### 6.4 可选：渠道同步任务表

如果后续需要做可靠同步、失败重试、外部状态回查，建议增加一张同步任务/事件表。

用途：

- 记录待同步项
- 记录重试次数
- 记录失败原因
- 支持手工补偿和审计

### 6.5 第一阶段最小本地模型建议

若按当前已确认范围推进，第一阶段本平台最少应实现以下本地模型：

- 渠道能力投影表
- 软件工厂学校 / 租户映射表
- 可选：同步任务 / 同步日志表

其中“学校 / 租户映射表”的用途是维护：

- 本平台学校或租户标识
- 对应软件工厂 `corp_id`
- 是否启用
- 备注

原因：

- `corp_id` 不是可从本平台现有 ID 自动推导的字段
- 该值由管理员手工维护，但“一个学校对应一个软件工厂 `corp_id`”
- 不建议在每条资源投影上反复手填 `corp_id`
- 更合理的是维护“学校 -> `corp_id`”映射，再在投影资源时自动带出

## 7. 调用链改造建议

## 7.1 基本原则

RobotFactory 调来的请求，最终必须进入本平台统一调用链，而不是在兼容 Controller 中直接拼协议去打下游。

原因：

- 统一调用链里已有日志记录
- 已有使用统计
- 已有 Trace
- 已有健康检查与可调用性校验
- 已有熔断降级

如果兼容层直接绕开统一调用链：

- 对方门户可以“看起来调通”
- 但本平台内部统计、治理、审计都会失真

### 7.2 推荐实现方式

建议新增一组 RobotFactory 兼容 Controller / Service，例如：

- `RobotFactoryMcpCompatController`
- `RobotFactoryProjectionService`
- `RobotFactoryPermissionService`
- `RobotFactorySyncService`

其中运行时兼容接口示意如下：

- `GET /regis/compat/robot-factory/mcp/{projectionCode}/sse`
- `POST /regis/compat/robot-factory/mcp/{projectionCode}/message?session_id=...`

运行流程建议如下：

1. 对方 AI 门户访问 `sse`
2. 本平台生成本地兼容会话 `session_id`
3. `tools/list` 返回该投影能力对应的工具定义
4. `tools/call` 收到调用请求后：
   - 解析 `toolName`
   - 解析 `arguments`
   - 提取并兼容 `_corp_id` / `_upload_dir` / `_server_address` / `_user_name` / `client_code`
   - 定位本平台真实资源
   - 调用统一网关内部服务
   - 将返回值转换为对方期望的 MCP 响应格式

### 7.3 关于 MCP 会话隔离

当前平台 `McpJsonRpcProtocolInvoker` 已使用 `ProtocolInvokeContext.apiKeyId()` 参与 MCP 会话缓存。

因此，RobotFactory 兼容层应避免所有外部请求共用同一个内部会话标识。建议做法：

- 对每个 RobotFactory 外部 `session_id`，生成一个独立的内部会话键
- 将该内部会话键作为 `ProtocolInvokeContext` 的 `apiKeyId` 或等价会话标识参与调用

这样可以确保：

- 不同机器人/不同会话之间的 MCP 上游会话不串线
- 多轮工具调用上下文能隔离

## 8. 与对方表结构的映射建议

### 8.1 `genie_external_agent`

建议由本平台“渠道能力投影表”生成并同步。

关键映射建议：

- `agent_name`：使用稳定、全局唯一、对外不可变的渠道能力编码
- `display_name`：来源于本平台资源展示名称
- `description`：来源于资源描述或工具说明
- `agent_type`：优先映射为 `mcp`
- `mode`：根据资源形态映射为 `TOOL` / `SUBAGENT` / `PAGE_APP`
- `spec_json.url`：指向本平台兼容 MCP SSE 地址
- `display_template`：根据资源输出类型映射
- `parameters_schema`：用于静态展示时可补充；动态工具优先通过 `tools/list`

### 8.2 `genie_agent_client_scope`

完整方案下，建议由本平台“渠道客户端范围表”同步生成。

映射含义：

- `client_code` 表示哪些机器人或客户端可以看到/挂载该能力
- `*` 表示全量放开

但按当前第一阶段范围：

- 本平台**暂不自动同步** `genie_agent_client_scope`
- 机器人绑定仍由软件工厂管理员在其页面中人工维护

### 8.3 `genie_agent_permission`

完整方案下，建议由本平台“渠道权限投影表”同步生成。

映射含义：

- 控制某权限组在某范围下是否允许使用该能力
- 这是对 `client_scope` 之后的二次裁剪，不应省略

但按当前第一阶段范围：

- 本平台**暂不自动同步** `genie_agent_permission`
- 权限设定仍由软件工厂管理员在其页面中人工维护

### 8.4 第一阶段 `genie_external_agent` 默认映射规则

仅基于当前已确认信息，第一阶段可先采用如下默认映射规则，无需等待所有增强口径全部明确后才开工。

- `corp_id`
  - 来源：由管理员配置的学校 / 租户与软件工厂 `corp_id` 映射
  - 规则：一个学校对应一个 `corp_id`
  - 全局能力时可由管理员显式选择 `NULL`
- `yn`
  - 默认：`1`
- `stop`
  - 默认：`0`
- `agent_name`
  - 来源：本平台生成的稳定、全局唯一、对外不可变能力编码
  - 不应直接使用可变展示名
- `display_name`
  - 来源：本平台资源展示名称
- `description`
  - 来源：资源描述、工具说明或兼容层补充文案
- `icon`
  - 第一阶段可为空
- `display_template`
  - 文件下载类：`file`
  - 图片生成类：`image`
  - 联网搜索类：`search_web`
  - 纯文本 / 普通工具类：可为空，走默认 tool 卡片
- `tags`
  - 第一阶段可为空，或由管理员维护
- `agent_type`
  - 第一阶段统一填：`mcp`
- `mode`
  - 第一阶段统一填：`TOOL`
- `max_concurrency`
  - 第一阶段默认：`1`
- `spec_json`
  - 第一阶段至少包含兼容层 MCP SSE 地址：
    - `url`
  - 可按需扩展其他字段，但不作为第一阶段阻塞项
- `workflow_source_json`
  - 第一阶段为空
- `intent_profile_json`
  - 第一阶段为空
- `intent_embedding_text`
  - 第一阶段为空
- `intent_profile_hash`
  - 第一阶段为空
- `intent_profile_status`
  - 第一阶段为空
- `intent_profile_update_time`
  - 第一阶段为空
- `allowed_tools`
  - 第一阶段可为空
- `denied_tools`
  - 第一阶段可为空
- `max_steps`
  - 第一阶段可为空
- `temperature`
  - 第一阶段可为空
- `system_prompt`
  - 第一阶段可为空
- `parameters_schema`
  - 有稳定静态参数模型的资源可填写 JSON Schema
  - 没有稳定模型时可先为空，运行时优先依赖 `tools/list`
- `hidden`
  - 默认：`0`
- `sort_order`
  - 默认：`0`
- `is_public`
  - 第一阶段建议默认：`1`
- `allowed_roles`
  - 第一阶段可为空
- `runtime_role`
  - 第一阶段统一填：`tool`
- `interaction_mode`
  - 第一阶段默认填：`sync`
  - 若后续确认某类能力需显式标为 `stream`，再单独放开
- `dispatch_mode`
  - 第一阶段统一填：`tool_sync`
- `quality_score`
  - 默认：`0.5`
- `avg_latency_ms`
  - 默认：`0`
- `success_rate`
  - 默认：`1`
- `avg_token_cost`
  - 默认：`0`
- `call_count`
  - 默认：`0`

## 9. 统计、熔断、降级是否能保留

结论：**可以保留，但前提是兼容层必须复用本平台统一调用链。**

### 9.1 能保留的前提

以下能力在复用统一调用链时可以继续保留：

- 调用日志
- 使用记录
- Trace
- 健康状态校验
- 熔断状态校验
- 降级响应

### 9.2 会丢失的情况

如果兼容层直接做下面这些事，则治理能力会部分或全部失效：

- 直接在兼容 Controller 中自己拼 HTTP 请求打真实资源地址
- 直接绕过 `UnifiedGatewayServiceImpl`
- 不写本平台使用记录和调用日志
- 不走本平台资源可调用性校验

因此，改造时必须把“兼容协议处理”和“真实资源执行”分开：

- 前者在兼容层
- 后者仍回流到本平台统一网关

## 10. 当前已确认信息与剩余待确认项

截至当前，架构路线已经明确，且已有一批关键事实得到确认。后续开发不应再围绕“是否要改核心模型”反复讨论，而应直接按“主平台 + RobotFactory 适配层”推进。

### 10.1 外部同步方式已确认

管理面同步方式已确认采用：

- 方案 A：**本平台直连对方数据库，写入 `genie_*` 表**

这意味着：

- 适配层必须具备独立的数据源配置或跨库写能力
- 需要显式设计幂等同步、失败补偿、重试与人工重推
- 不应把对方三张表直接当作本平台主模型，而应保留本地“渠道投影模型”，再做外部同步

### 10.2 对方关键表结构已确认

对接所需的三张关键表已经确认：

- `genie_external_agent`
- `genie_agent_client_scope`
- `genie_agent_permission`

其中已确认的关键约束如下：

- `genie_external_agent.agent_name` 全局唯一，应作为对方侧能力稳定编码
- `genie_external_agent.agent_type` 取值至少包含 `mcp` / `http_api`
- `genie_external_agent.mode` 取值至少包含 `TOOL` / `SUBAGENT` / `PAGE_APP` / `ALL`
- `genie_external_agent.display_template` 决定门户前端卡片形态
- `genie_external_agent.corp_id = NULL` 表示全局能力
- `genie_agent_client_scope` 有唯一键 `uk_agent_client(agent_id, client_code)`，适合做幂等同步
- `genie_agent_permission` 无唯一键，权限同步不能直接无脑插入，需自行控重或采用“按 agent 重建”的同步策略

设计结论：

- 本平台内部应保留 `external_agent_id`，用于回写对方 `genie_external_agent.id`
- 本平台内部应为权限投影定义“自然唯一键”，至少覆盖 `agent_id + group_id + scope_type + corp_id + client_code + permission`
- `agent_name` 必须在本平台适配层中生成并长期稳定，不应随着资源标题、展示名称变化而变化

### 10.3 运行协议已确认

结合 `D:\LantuConnect\robotagents` 工程代码，可以确认对方运行面协议要求如下：

- 使用 MCP SSE
- 建链方式为 `GET /sse`
- 服务端通过 SSE 首帧返回 `message?session_id=...`
- 后续交互通过 `POST /message?session_id=...`
- 至少支持 JSON-RPC 方法：
  - `initialize`
  - `notifications/initialized`
  - `tools/list`
  - `tools/call`

已确认的会话约束：

- 空闲超时：10 分钟
- 最大生命周期：30 分钟

已确认的上下文注入参数：

- `_corp_id`
- `_upload_dir`
- `_server_address`
- `_user_name`

已确认的工具调用附加参数：

- 部分工具强依赖 `client_code`
- `data_fetcher` 一类工具在没有 `client_code` 时会直接报错

设计结论：

- 本平台兼容层必须维护自己的 `session_id`
- 不同外部会话必须映射为不同内部会话标识，避免上游 MCP 会话串线
- `tools/call` 进入兼容层后，应先处理上下文参数，再回流到本平台统一调用链

### 10.4 文件型工具输出协议已部分确认

从 `robotagents` 代码可确认，文件类工具会依赖对方注入的上下文来决定落盘目录与下载 URL。

当前已确认：

- 文件落盘会优先使用 `_upload_dir`
- 下载 URL 会优先使用 `_server_address`
- 代码中的标准下载 URL 为：
  - `/support/cache/{corp_id}/genies/{user_name}/{fileName}`
- 代码还兼容不带 `user_name` 的旧链接：
  - `/support/cache/{corp_id}/genies/{fileName}`

仍需注意：

- `robotagents` README 中还存在较旧的 `/support/cache/genie/{fileName}` 口径，文档与实现并非完全一致
- 最终应以对方当前生产实现和门户实际访问方式为准

设计结论：

- 本平台如投影文件型能力，返回结果中的 URL 必须能被对方门户直连
- 兼容层应保留对 `_upload_dir` / `_server_address` 的处理能力

### 10.5 缓存依赖已确认，但刷新机制仍待确认

从 `robotagents` 可确认，对方运行面并不只是直接查库，还依赖 Redis 中的能力缓存。

已确认的关键缓存键：

- `robot:agents:external_agent_all`

同时，token 版本刷新还依赖：

- `robot:agents:token_version`

目前仍需确认：

- 写入 `genie_*` 表后，是否必须主动删除或重建 `robot:agents:external_agent_all`
- 刷新入口是直接删 Redis key、写版本号、调用后台任务，还是由对方平台异步重建
- 适配层写库后多久对门户可见

这是当前最重要的待确认项之一，因为只写表不一定会立刻生效。

### 10.6 鉴权方式已确认，第一阶段兼容层先走白名单

从 `robotagents` 可确认：

- 对方本地 Agent 服务默认使用 `Authorization: Bearer <token>`
- token 数据来自 `agent_access_token`
- token 具备 `allowed_tools` 级别的工具访问控制
- `health` / `status` / `support/cache` 等路径可被白名单放行

但对于“软件工厂调用本平台兼容层”这一条链路，当前已确认第一阶段方案为：

- 软件工厂**不会修改其现有代码**
- 兼容层先采用**白名单放行**
- 第一阶段不以 Bearer Token 作为落地阻塞项

设计结论：

- 兼容层应支持基于来源 IP / 网络边界的白名单控制
- 同时保留后续升级到服务密钥 / Bearer Token 的能力，但不作为第一阶段阻塞项

### 10.7 `client_code` / `corp_id` / `group_id` 业务语义已部分明确

从代码可以确认：

- `client_code` 在部分工具中是必填运行参数
- `corp_id` 会进入文件存储路径、下载路径和数据访问逻辑

当前已明确：

- `corp_id` 由管理员手工维护
- 该值会变动，但遵循“**一个学校对应一个软件工厂 `corp_id`**”的规则
- 因此 `corp_id` 不应从本平台现有学校 ID 自动推导，也不应写死在代码中

仍需进一步了解但不阻塞第一阶段的内容：

- `client_code` 究竟代表机器人、门户客户端，还是知识空间 / 工作空间
- `group_id` 在软件工厂侧由谁维护
- `scope_type = global/corp/client` 的精确裁剪边界
- 一个能力是按全局注册后再做权限裁剪，还是按租户分别注册

### 10.8 多资源形态映射规则已可先按默认规则落地，细节仍可后续细化

本平台资源类型不一定与对方 `agent_type` / `mode` / `display_template` 一一对应。

已可先按本章 `8.4` 的第一阶段默认映射规则落地。

后续仍可继续细化：

- `mcp` 资源如何映射
- `skill` 如何映射
- `agent` 如何映射
- `app` 是否映射为 `PAGE_APP`
- `dataset` 是否允许直接投影，还是只能通过工具包装暴露
- `runtime_role` / `interaction_mode` / `dispatch_mode` 的合法组合

### 10.9 下线与回滚策略已基本明确，补偿机制仍需补齐

由于已确认采用“直连对方数据库写表”，后续必须补齐同步异常场景设计。

当前已明确：

- 下线/解绑/改权限不由第一阶段适配层接管
- 第一阶段至少要做到：资源下线时同步清理或失效对应的 `genie_external_agent` 数据

建议第一阶段优先采用：

- 改 `yn=0`
- 必要时补充 `hidden=1`

而不是直接物理删除。这样更稳，也更方便恢复。

仍需补齐：

- 本平台是否保留“待同步 / 同步失败”状态
- 是否支持自动重试
- 是否支持人工重推

## 11. 推荐实施步骤

结合当前已确认范围，建议按“第一阶段 MVP + 后续增强”落地。

### 第一期：完成第一阶段 MVP

输出内容：

- 软件工厂适配菜单与页面
- 学校 / 租户到 `corp_id` 的映射配置
- 资源到 `genie_external_agent` 的投影配置
- `genie_external_agent` 同步服务
- 软件工厂兼容 MCP SSE 接口
- 白名单放行
- 下线失效机制

目标：

- 让本平台资源能注册到软件工厂，并被软件工厂按原有方式绑定与调用

### 第二期：补齐缓存刷新与同步稳定性

实现内容：

- 接入缓存刷新机制
- 补齐同步日志
- 补齐失败重试与人工重推
- 补齐同步状态展示

目标：

- 让“写入注册表后立即可见”变成稳定闭环

### 第三期：视需要接管机器人绑定

实现内容：

- 新增渠道客户端范围表
- 同步 `genie_agent_client_scope`
- 增加“绑定机器人”管理页面

目标：

- 由本平台接管软件工厂侧的机器人绑定投影

### 第四期：视需要接管权限投影

实现内容：

- 同步 `genie_agent_permission`
- 增加权限规则配置页面
- 补齐权限控重、补偿与回滚

目标：

- 由本平台接管软件工厂侧的权限投影

## 12. 最终方案结论

本次对接的正确方向不是“把蓝图连接改造成 RobotFactory”，而是：

**蓝图连接继续做资源主平台；新增 RobotFactory 兼容适配层，让对方平台按自己的注册/绑定/权限/调用流程消费蓝图连接的资源。**

截至当前，第一阶段职责边界建议如下：

- 本平台负责：
  - 资源主数据
  - 资源发布与生命周期
  - 渠道投影
  - `genie_external_agent` 同步
  - MCP 兼容入口
  - 统一调用治理
  - 同步与补偿

- 对方平台负责：
  - 机器人绑定
  - 权限组配置
  - 门户展示
  - 门户侧调度
  - 第一阶段的客户端范围配置
  - 第一阶段的权限配置

只有采用“主平台 + 渠道兼容层”的方式，才能同时满足：

- 对方 AI 门户无缝衔接
- 本平台不被单一渠道绑死
- 统计、熔断、降级、Trace 等平台能力继续有效
- 后续还能支持师生端和第三方平台

## 13. 当前文档的直接用途

本文档可直接作为以下事项的基础说明：

- 与公司平台方确认对接边界
- 在本项目内评审改造方案
- 作为后续数据库设计与接口设计输入
- 作为开发任务拆分依据

如进入开发阶段，下一步应继续补充两类文档：

1. 数据库设计文档
2. RobotFactory 兼容接口详细设计文档

## 14. 截至当前的直接实施清单

下次进入开发时，可直接按以下清单推进，而无需重新讨论总体方向。

### 14.1 菜单与页面

建议在超管侧新增一级或二级菜单：`软件工厂适配`

建议至少包含以下页面：

- 适配概览页
  - 展示已投影资源数、待同步数、失败数、最近同步时间
- 能力投影列表页
  - 展示本平台资源与 `agent_name`、`display_name`、`agent_type`、`mode`、`display_template` 的映射
- 投影详情/编辑页
  - 维护 `spec_json`、`parameters_schema`、`runtime_role`、`interaction_mode`、`dispatch_mode`
- 学校 / 租户 `corp_id` 映射页
  - 维护“一个学校对应一个软件工厂 `corp_id`”的映射
- 同步任务 / 同步日志页
  - 展示同步成功、失败、失败原因、重试入口

### 14.2 本地数据模型

按当前第一阶段范围，建议本平台内部至少新增以下两张本地表：

- 渠道能力投影表
- 软件工厂学校 / 租户映射表

建议关键字段如下：

- 渠道能力投影表：
  - `channel_code`
  - `resource_id`
  - `resource_type`
  - `corp_id`
  - `agent_name`
  - `display_name`
  - `description`
  - `display_template`
  - `agent_type`
  - `mode`
  - `runtime_role`
  - `interaction_mode`
  - `dispatch_mode`
  - `spec_json`
  - `parameters_schema`
  - `enabled`
  - `external_agent_id`
  - `sync_status`
  - `sync_message`
  - `sync_time`
- 软件工厂学校 / 租户映射表：
  - `school_id` 或等价学校标识
  - `school_name`
  - `corp_id`
  - `enabled`
  - `remark`
  - `create_time`
  - `update_time`

如需补齐同步稳定性，可再增加：

- 同步任务 / 同步日志表

### 14.3 外部同步策略

建议同步策略如下：

- `genie_external_agent`
  - 按 `agent_name` 查找
  - 已存在则更新
  - 不存在则插入
  - 回写 `external_agent_id`
- 第一阶段暂不自动同步 `genie_agent_client_scope`
- 第一阶段暂不自动同步 `genie_agent_permission`
- 资源下线时，第一阶段优先改对方 `genie_external_agent.yn=0`

### 14.4 后端模块建议

建议新增独立包，例如：

- `com.lantu.connect.compat.robotfactory.controller`
- `com.lantu.connect.compat.robotfactory.service`
- `com.lantu.connect.compat.robotfactory.entity`
- `com.lantu.connect.compat.robotfactory.mapper`
- `com.lantu.connect.compat.robotfactory.sync`

建议的核心类包括：

- `RobotFactoryProjectionController`
- `RobotFactoryProjectionService`
- `RobotFactoryCorpMappingService`
- `RobotFactorySyncService`
- `RobotFactoryMcpCompatController`
- `RobotFactorySessionService`

### 14.5 运行时兼容接口

建议至少实现以下兼容接口：

- `GET /regis/compat/robot-factory/mcp/{projectionCode}/sse`
- `POST /regis/compat/robot-factory/mcp/{projectionCode}/message?session_id=...`

接口职责：

- `sse`
  - 创建外部会话
  - 返回 `message?session_id=...`
- `message`
  - 处理 `initialize`
  - 处理 `notifications/initialized`
  - 处理 `tools/list`
  - 处理 `tools/call`
  - 提取 `_corp_id` / `_upload_dir` / `_server_address` / `_user_name` / `client_code`
  - 把最终执行请求回流到本平台统一调用链

### 14.6 不应做的事

下次开发时，应避免再次走偏到以下方向：

- 不直接把本平台核心资源表改造成 `genie_external_agent`
- 不让软件工厂逻辑入侵本平台统一资源主模型
- 不在兼容 Controller 里直接拼 HTTP 请求绕开统一网关
- 不把“超管菜单”误认为全部工作；页面只是管理入口，真正关键的是适配模块、同步服务与运行时兼容层
- 不在第一阶段提前接管机器人绑定和权限配置，除非范围明确扩大
