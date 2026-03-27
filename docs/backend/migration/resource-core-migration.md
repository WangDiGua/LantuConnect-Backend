# 资源主表重构落地记录

## 目标
- 建立统一资源主表 `t_resource` + 各类型扩展表（Agent/Skill/MCP/App/Dataset）。
- 将现有业务表数据迁移到统一模型，供统一目录与解析网关读取。

## 已落地内容
- 基线建表与初始化已并入：`sql/lantu_connect.sql`。
- 增量变更规范见：`sql/migrations/README.md`。
- 新建表：
  - `t_resource`
  - `t_resource_agent_ext`
  - `t_resource_skill_ext`
  - `t_resource_mcp_ext`
  - `t_resource_app_ext`
  - `t_resource_dataset_ext`
  - `t_resource_relation`
- 完成迁移来源：
  - `t_agent` -> `t_resource` + `t_resource_agent_ext`
  - `t_skill` -> `t_resource` + `t_resource_skill_ext`
  - `t_mcp_server` -> `t_resource` + `t_resource_mcp_ext`
  - `t_smart_app` -> `t_resource` + `t_resource_app_ext`
  - `t_dataset` -> `t_resource` + `t_resource_dataset_ext`
  - `t_dataset_agent_rel` -> `t_resource_relation`
  - `t_skill.parent_id` -> `t_resource_relation`

## 网关切换
- `UnifiedGatewayServiceImpl` 已切换为读取 `t_resource*` 表，不再直接依赖旧资源业务表进行目录与解析。
- 统一调用 `invoke` 行为保持不变，仍按解析结果进行 HTTP 调用并写入 `t_call_log`。

## 本次验证
- 执行迁移后统计：
  - `agent`: 6
  - `app`: 3
  - `dataset`: 3
  - `skill`: 8
- 工程编译通过：`./mvnw.cmd -DskipTests compile`。

## 后续建议
- 增加“新旧模型一致性校验脚本”（行数、关键字段哈希、抽样比对）。
- 推进双读双写：写入先落旧表，异步/同步写 `t_resource*`，灰度观察后再切主。
