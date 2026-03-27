# 06 改造顺序与验收

## 1) 改造顺序（建议）

1. 资源中心：五类 `*-register/*-list` + 生命周期按钮  
2. 审核中心：`audit-center` 五类审核页  
3. 授权中心：`resource-grant-management`  
4. 五市场：默认 `published` + resolve/invoke  
5. 用户与权限管理  
6. 监控与系统配置  
7. 壳层与路由守卫  
8. SDK/沙箱/Playground

## 2) 端到端验收路径

### App 闭环
`app-register -> app-list submit -> audit-center/app-audit approve -> publish -> app-market resolve/open -> grant -> invoke`

### MCP 闭环
`mcp-register -> mcp-server-list submit -> audit-center/mcp-audit approve -> publish -> mcp-market resolve/invoke`

### 授权闭环
未授权 invoke 403 -> 新增授权 -> invoke 成功 -> 撤销授权 -> invoke 403

## 3) 角色验收清单

### platform_admin
- 五类资源全链路可操作
- 审核中心三动作齐全
- 授权新增/撤销立即生效

### dept_admin
- 可见页面符合权限收敛
- 审核状态转换正确（`testing` 后才可发布）

### developer
- 五类资源可注册并提审
- 市场可见性与发布状态一致
- 调用授权前后行为正确

## 4) 回归检查

- 菜单无重复语义入口
- 旧接口无残留写操作
- 空态/错态/权限态覆盖完整
- 查询条件可回放（URL 或本地状态）

## 5) 逐角色页面回归脚本（可直接点测）

### platform_admin
1. 新建 `mcp` 资源并保存草稿。  
2. 在列表提审。  
3. 到 `audit-center/mcp-audit` 通过并发布。  
4. 到 `mcp-market` 检索并执行 `resolve/invoke`。  
5. 到授权中心新增授权，再撤销授权，验证调用方结果变化。  
6. 在评价区提交评论并点赞 helpful，验证列表刷新。

### dept_admin
1. 进入 `audit-center` 处理 `app` 审核单（通过->发布）。  
2. 验证无权限配置页是否隐藏或明确 403。  
3. 进入监控页筛选日志并查看 trace。  
4. 对失败请求执行重试，验证错误提示与恢复。

### developer
1. 在 `my-publish` 创建 `agent` 并提审。  
2. 在市场详情点击 `立即使用`，走 `resolve/invoke`。  
3. 若 403，按“申请授权”流程完成后重试成功。  
4. 在详情页完成评分评论与 helpful 操作。  
5. 进入 `profile/preferences` 修改并保存，验证回显。

## 5.1 全页面回归矩阵（逐页可勾选）

> 口径：以 `frontend-full-spec.md` A2.1/A2.2 为页面真值；以下每个 slug 必测，不允许仅按 `*` 通配组判定完成。

### admin 端（A2.1）

| 页面 slug | 验收动作 |
|---|---|
| `dashboard` | 概览卡刷新 + 时间筛选 + 错误重试 |
| `health-check` | 健康摘要展示 + 跳转健康配置 |
| `usage-statistics` | 统计维度切换 + 图表回显 |
| `data-reports` | 筛选报表 + 明细加载 |
| `agent-list` | 列表筛选 + 行操作可用性 |
| `agent-register` | 保存草稿 + 提交审核 |
| `agent-detail` (`direct-url-only`) | 仅直链访问；详情/版本/评价可正常加载 |
| `agent-audit` | 通过/驳回/发布三动作完整 |
| `agent-monitoring` | 指标查询 + 日志联动 |
| `agent-trace` | traceId 查询 + 明细展开 |
| `skill-list` | 列表筛选 + 行操作可用性 |
| `skill-register` | 保存草稿 + 提交审核 |
| `skill-audit` | 通过/驳回/发布三动作完整 |
| `mcp-server-list` | 列表筛选 + 行操作可用性 |
| `mcp-register` | 保存草稿 + 提交审核 |
| `mcp-audit` | 通过/驳回/发布三动作完整 |
| `app-list` | 列表筛选 + 行操作可用性 |
| `app-register` | 保存草稿 + 提交审核 |
| `app-audit` | 通过/驳回/发布三动作完整 |
| `dataset-list` | 列表筛选 + 行操作可用性 |
| `dataset-register` | 保存草稿 + 提交审核 |
| `dataset-audit` | 通过/驳回/发布三动作完整 |
| `provider-list` | 列表可达 + 与授权中心职责不混淆 |
| `provider-create` | 创建提交流程可用 |
| `user-list` | 用户增改查 + 状态回显 |
| `role-management` | 角色增改删 + 系统角色保护校验 |
| `organization` | 组织树增改删 + 挂载校验 |
| `api-key-management` | API Key 新建 + 撤销 |
| `resource-grant-management` | 新增授权 + 撤销授权 + 调用影响验证 |
| `developer-applications` | 入驻申请审批（通过/驳回） |
| `monitoring-overview` | KPI + 告警摘要展示 |
| `call-logs` | 日志检索 + 分页 + 重试 |
| `performance-analysis` | 性能趋势筛选 + 维度切换 |
| `alert-management` | 告警筛选 + 状态更新 |
| `alert-rules` | 规则增改删 + dry-run |
| `health-config` | 探针配置增改删 |
| `circuit-breaker` | 熔断打开/恢复操作 |
| `tag-management` | 标签增改删 + 批量导入 |
| `model-config` | 模型配置增改删查 |
| `security-settings` | 安全策略保存与回显 |
| `quota-management` | 配额增改删查 |
| `rate-limit-policy` | 限流规则增改删查 |
| `access-control` | ACL 发布动作可用 |
| `audit-log` | 审计日志检索 |
| `sensitive-words` | 敏感词增改删 + 检测接口联动 |
| `announcements` | 公告增改删查 |
| `api-docs` | 文档页可达 + 内容加载 |
| `sdk-download` | SDK 列表加载 + 下载行为 |
| `api-playground` | resolve/invoke 调试 + 结果回显 |
| `developer-statistics` | 开发者统计筛选与展示 |

### user 端（A2.2）

| 页面 slug | 验收动作 |
|---|---|
| `hub` | 推荐流加载 + 筛选 + 跳详情 |
| `workspace` | 工作台摘要加载 + 快捷入口跳转 |
| `my-agents` | 我的 Agent 列表加载/分页 |
| `authorized-skills` | 已授权技能加载 + 立即使用入口 |
| `my-favorites` | 收藏列表 + 取消收藏 |
| `quick-access` (`direct-url-only`) | 仅直链访问；快捷卡片跳转可用 |
| `recent-use` | 最近使用记录加载 |
| `agent-market` | 列表筛选 + 详情 + 使用 |
| `skill-market` | 列表筛选 + 详情 + 使用 |
| `mcp-market` | 列表筛选 + 详情 + 使用 |
| `app-market` | 列表筛选 + 详情 + 使用 |
| `dataset-market` | 列表筛选 + 详情 + 申请使用 |
| `my-agents-pub` | 发布总览计数与跳转 |
| `resource-center` | 统一资源中心列表与状态机动作 |
| `agent-list` | 列表筛选 + 行操作 |
| `agent-register` | 保存草稿 + 提交审核 |
| `skill-list` | 列表筛选 + 行操作 |
| `skill-register` | 保存草稿 + 提交审核 |
| `mcp-server-list` | 列表筛选 + 行操作 |
| `mcp-register` | 保存草稿 + 提交审核 |
| `app-list` | 列表筛选 + 行操作 |
| `app-register` | 保存草稿 + 提交审核 |
| `dataset-list` | 列表筛选 + 行操作 |
| `dataset-register` | 保存草稿 + 提交审核 |
| `my-skills` | 我的 Skill 列表加载/分页 |
| `usage-records` | 调用记录检索 + 分页 |
| `usage-stats` | 统计图加载 + 时间维度切换 |
| `profile` | 资料修改 + 会话管理 |
| `preferences` | 偏好保存 + API Key 管理 |

## 完整性检查清单

- [x] 改造顺序覆盖资源中心/审核中心/授权中心/市场/治理/壳层/SDK沙箱
- [x] 至少三条端到端验收路径（App/MCP/授权）完整
- [x] 三角色验收口径完整
- [x] 回归检查项完整
- [x] 逐角色页面回归脚本完整
- [x] 全页面回归矩阵完整（admin + user）
