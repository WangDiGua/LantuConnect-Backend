# 人员展示字段统一改造说明（2026-03-26）

## 1. 改造目标
- 对所有“返回中含人员 ID”的对象补充可直接展示的人名字段（优先 `*Name`）。
- 保留原有 ID 字段，接口保持向后兼容。
- 服务层统一使用批量 `userId -> name` 解析，避免 N+1 查询。

## 2. 统一规则
- 命名优先级：`createdByName` / `reviewerName` / `applicantName` / `grantedByName`。
- 人名解析优先级：`realName` -> `username` -> `user-{id}`。
- ID 字段不删除：如 `createdBy`、`reviewerId`、`applicantId` 均保留。

## 3. 本次新增字段清单
- 公告 `Announcement`：`createdByName`
- 敏感词 `SensitiveWord`：`createdByName`
- 资源管理 `ResourceManageVO`：`createdByName`
- 资源授权 `ResourceGrantVO`：`grantedByName`
- 授权申请 `GrantApplicationVO`：`applicantName`、`reviewerName`
- 审核项 `AuditItem`：`submitterName`、`reviewerName`
- 开发者申请 `DeveloperApplication`：`userName`、`reviewedByName`
- API Key `ApiKey`：`createdByName`
- 调用日志 `CallLog`：`username`

## 4. 兼容策略（前端）
- 旧字段继续可用，不需要立即改造旧逻辑。
- 前端展示建议优先读取新增字段：
  - `createdByName` / `reviewerName` / `applicantName` / `grantedByName` / `username`
- 若新增字段为空（极少数历史脏数据），可回退显示原 ID。

## 5. 数据库影响
- 无新增字段、无 DDL 变更。
- 新增字段均为后端响应层计算字段（非持久化）。

## 6. 代码实现点
- 新增统一组件：`UserDisplayNameResolver`
  - 批量查询用户（`in user_id`）并返回名称映射。
- 在以下服务中批量注入名称并回填响应字段：
  - `AnnouncementServiceImpl`
  - `SensitiveWordService`
  - `ResourceRegistryServiceImpl`
  - `ResourceInvokeGrantService`
  - `GrantApplicationServiceImpl`
  - `AuditServiceImpl`
  - `DeveloperApplicationServiceImpl`
  - `UserMgmtServiceImpl`
  - `MonitoringServiceImpl`
