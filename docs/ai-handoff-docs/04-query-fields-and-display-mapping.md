# 04 查询参数与字段展示映射

## 1) 列表查询参数规范

### 通用字段

- 分页：`page`、`pageSize`
- 过滤：`keyword`、`status`、`resourceType`
- 排序：`sortBy`、`sortOrder`
- 时间：`range` 或 `startDate/endDate`

### 资源目录增强字段

- `categoryId`
- `tags[]`

## 2) 默认筛选与排序

- 市场页默认：`status=published`
- 审核页默认：`status=pending_review`
- 资源管理默认排序：`updateTime desc`
- 授权列表默认：`expiresAt asc`（优先即将到期）

## 3) 返回字段到 UI 映射（最小可用集）


| 接口                                    | 页面       | 必显字段                                         | 可选字段                                      |
| ------------------------------------- | -------- | -------------------------------------------- | ----------------------------------------- |
| `GET /catalog/resources`              | 市场页      | `displayName/resourceType/status`            | `resourceCode/sourceType/updateTime/tags` |
| `POST /catalog/resolve`               | 详情/调用前弹窗 | `invokeType/endpoint/status`                 | `version/spec`                            |
| `POST /invoke`                        | 调用结果面板   | `traceId/statusCode/latencyMs`               | `body/requestId`                          |
| `GET /resource-center/resources/mine` | `*-list` | `displayName/status/updateTime`              | `resourceCode/resourceType/version`       |
| `GET /audit/resources`                | 审核页      | `displayName/resourceType/status/submitTime` | `submitter/reviewer`                      |
| `GET /resource-grants`                | 授权中心     | `granteeApiKeyId/actions/expiresAt/status`   | `grantId/resourceType/resourceId`         |
| `GET /monitoring/call-logs`           | 调用日志     | `traceId/statusCode/latencyMs/time`          | `resourceType/resourceId/caller`          |
| `GET /notifications`                  | 消息中心     | `title/read/time`                            | `content/type`                            |
| `GET /reviews`                        | 评价区      | `rating/content/helpfulCount`                | `author/createdAt`                        |
| `GET /auth/sessions`                  | 会话管理     | `device/ip/lastActiveAt/current`             | `os/browser/location`                     |

## 3.1 字段落位图（页面分区级）

### 资源详情页（Agent/Skill/MCP/App）
- `TopSummary`：`displayName/resourceType/status/tags`
- `MetaInfo`：`resourceCode/version/updateTime/sourceType`
- `InvokeConfig`：`invokeType/endpoint/spec`
- `RatingSummary`：`avgRating/totalReviews/ratingDistribution`
- `ReviewList`：`rating/content/helpfulCount/author/createdAt`
- `InvokeResultPanel`：`traceId/statusCode/latencyMs/body`

### 审核中心页
- `AuditTable`：`displayName/resourceType/status/submitter/submitTime`
- `ActionModal`：`rejectReason`（驳回时必填）
- `StatusBadge`：`pending_review/testing/published/rejected`

### 授权中心页
- `GrantTable`：`granteeApiKeyId/actions/expiresAt/status`
- `GrantModal`：`resourceType/resourceId/granteeApiKeyId/actions/expiresAt`

### 评论弹窗
- `ReviewModal`：`rating(1-5)` + `content`
- `SubmitResult`：成功关闭并刷新；失败保留输入并提示


## 4) 字段缺失与兼容策略

- 后端无该字段：前端不造字段，不用 mock 字段冒充真值。
- 字段可空：展示占位符 `--`，不要报错。
- 字段新增：优先展示在详情区域，再评估是否进入列表列。

## 完整性检查清单

- [x] 通用查询参数（分页/过滤/排序/时间）完整
- [x] 资源目录增强参数（`categoryId/tags[]`）完整
- [x] 默认筛选与默认排序规则完整
- [x] 关键接口返回字段展示映射完整
- [x] 返回字段落位到页面分区完整

