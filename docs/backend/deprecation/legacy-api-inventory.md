# 旧接口清单（Phase 3）

## 当前策略

- 第一阶段已启用：
  - 旧接口自动加废弃响应头
  - 旧接口写操作统一返回 `410 Gone`
- 第二阶段（旧资源控制器物理删除）已完成。
- 当前进入“观察与收口”阶段：持续观察管理路由流量并评估是否继续缩减。

## 第二阶段执行状态

- 已完成物理删除：
  - `com.lantu.connect.agent.controller.AgentController`
  - `com.lantu.connect.agent.controller.AgentVersionController`
  - `com.lantu.connect.skill.controller.SkillController`
  - `com.lantu.connect.mcp.controller.McpServerController`
  - `com.lantu.connect.app.controller.AppController`
  - `com.lantu.connect.dataset.controller.DatasetController`
  - `com.lantu.connect.dataset.controller.ProviderController`
  - `com.lantu.connect.dataset.controller.CategoryController`
- 当前批次范围内旧资源接口控制器已全部完成物理删除。

## 深度清理状态（仅旧控制器关联代码）

- 已完成删除：
  - Agent/Skill/MCP/App/Dataset/Provider/Category 相关旧 Service 接口与实现
  - 上述模块仅旧控制器使用的 DTO 请求/查询对象
- 保留：
  - 实体与 Mapper（仍用于统一网关目录/解析能力）
  - 仍被现有接口使用的 Tag 相关模块

## 统一替代接口

- `GET /catalog/resources`
- `GET /catalog/resources/{type}/{id}`
- `POST /catalog/resolve`
- `POST /invoke`

## 当前旧接口范围

- `/v1/**`
- `/agents/**`

## 历史第二阶段删除范围（已完成）

- Agent 旧路由
  - `com.lantu.connect.agent.controller.AgentController`
  - `com.lantu.connect.agent.controller.AgentVersionController`
- V1 资源路由
  - `com.lantu.connect.skill.controller.SkillController`
  - `com.lantu.connect.mcp.controller.McpServerController`
  - `com.lantu.connect.app.controller.AppController`
  - `com.lantu.connect.dataset.controller.DatasetController`
  - `com.lantu.connect.dataset.controller.ProviderController`
  - `com.lantu.connect.dataset.controller.CategoryController`

## 观察中模块（第三阶段）

- 待观察流量后决定的管理路由
  - `com.lantu.connect.sysconfig.controller.*`
  - `com.lantu.connect.monitoring.controller.*`

## 删除前校验项

- 最近 7 天接口调用量
- 替代接口联调状态
- 依赖方通知状态
- 删除审批签字
