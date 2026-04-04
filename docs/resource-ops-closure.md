# 超管「资源与运营」功能闭环说明

侧栏菜单项、前端页面、后端接口与五类资源（Agent / Skill / MCP / App / Dataset）的对应关系如下。权限：各页在超管侧通常要求 `platform_admin` 或注解中的读写权限组合；监控类接口统一需要 `monitor:view`。

## 统一资源中心

| 项目 | 说明 |
|------|------|
| 前端 | `ResourceCenterManagementPage`，路由键 `resource-catalog` |
| 主要 API | `/resource-center/resources`（列表、版本、状态等，具体见 `ResourceCenterController`） |
| 外联技能目录 | `/resource-center/skill-external-catalog`（与「市场」配置相关） |
| 五类资源 | 以 `resource_type` / 业务枚举贯穿列表与操作 |

## 技能在线市场

| 项目 | 说明 |
|------|------|
| 前端 | `SkillExternalMarketPage`，路由键 `skill-external-market` |
| 主要 API | 资源中心外联目录 + 市场/标签相关接口（与 `market` / `resource-center` 模块协作） |
| 五类资源 | 以 Skill 为主；可关联 Agent / MCP 等展示 |

## 运行监控

| 项目 | 说明 |
|------|------|
| 前端 | `AgentMonitoringPage`，路由键 `agent-monitoring` |
| 主要 API | `GET /monitoring/kpis`，`GET /monitoring/performance`（可选 `resourceType`），`GET /monitoring/call-summary-by-resource`，`GET /monitoring/resources/{type}/{id}/quality-history` |
| 数据表 | `t_call_log`（`resource_type` 区分调用目标类型；空值在汇总中为 `unknown`） |
| 五类资源 | `performance` / `call-summary-by-resource` / `quality-history` 均按类型过滤或分组 |

## 调用追踪

| 项目 | 说明 |
|------|------|
| 前端 | `AgentTracePage`，路由键 `agent-trace` |
| 主要 API | `GET /monitoring/traces`（分页、keyword） |
| 数据表 | `t_trace_span`（`parent_id` 树形；`tags` JSON 可带 `resource_type`） |
| 五类资源 | 以后端写入的 `tags` / 运维约定为准；前端聚合成树展示 |

## 资源审核

| 项目 | 说明 |
|------|------|
| 前端 | `ResourceAuditList`，路由键 `resource-audit` |
| 主要 API | `GET/PATCH` `/audit/resources`（见 `AuditController`） |
| 数据表 | 审核队列表项与 `t_resource` 等关联；**种子数据需指向真实资源** 否则通过后状态同步可能无行可更新 |
| 五类资源 | 审核单上的资源类型与统一资源中心一致 |

## Provider 管理

| 项目 | 说明 |
|------|------|
| 前端 | `ProviderManagementPage`，路由键 `provider-list` / `provider-create` |
| 主要 API | `GET/POST/PUT/DELETE /providers`（见 `ProviderController`） |
| 数据表 | `t_provider` |

## 演示数据

可执行 `sql/seed_resource_ops_demo.sql`，为监控与追踪补充**近时间内**的 `t_call_log`、`t_trace_span` 及示例 `t_provider`，便于「运行监控」「调用追踪」页面立即有数可查。
