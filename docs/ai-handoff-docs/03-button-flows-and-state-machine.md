# 03 按钮流程与状态机

## 1) 资源状态机（统一）

`draft -> pending_review -> testing -> published -> deprecated`  
异常分支：`rejected`

## 2) 生命周期按钮流程卡

### 保存草稿
- 前置：必填字段通过校验
- 接口：`POST /resource-center/resources` 或 `PUT /resource-center/resources/{id}`
- 成功：toast + 返回列表 + 高亮记录
- 失败：保留表单输入并显示字段错误

### 提交审核
- 前置：`draft/rejected/deprecated`
- 接口：`POST /resource-center/resources/{id}/submit`
- 成功：状态 `pending_review` + “查看审核进度”
- 失败：提示状态不合法或权限不足

### 撤回审核
- 前置：`pending_review`
- 接口：`POST /resource-center/resources/{id}/withdraw`
- 成功：状态回 `draft`
- 失败：提示“仅审核中可撤回”

### 审核通过
- 前置：`pending_review`
- 接口：`POST /audit/resources/{id}/approve`
- 成功：状态 `testing`
- 失败：提示并刷新行状态

### 发布上架
- 前置：`testing`
- 接口：`POST /audit/resources/{id}/publish`
- 成功：状态 `published`，市场可见
- 失败：阻断文案 `当前资源未进入 testing，不能直接发布`

### 下线
- 前置：`published/testing`
- 接口：`POST /resource-center/resources/{id}/deprecate`
- 成功：状态 `deprecated`
- 失败：提示并刷新

## 3) 授权与调用按钮流程卡

### 申请 API 授权（前端引导）
- 前置：非 owner
- 接口：无后端申请单（仅前端引导）
- 成功：引导 owner 到授权中心执行 `POST /resource-grants`

### 新增授权
- 前置：owner/管理员
- 接口：`POST /resource-grants`
- 请求：`resourceType/resourceId/granteeApiKeyId/actions/expiresAt?`
- 成功：授权记录出现；调用方可 invoke

### 撤销授权
- 接口：`DELETE /resource-grants/{grantId}`
- 成功：调用方再次 invoke 返回 403

### 立即使用 / 调用
- 流程：`POST /catalog/resolve` -> `POST /invoke`
- 成功：展示 `traceId/statusCode/latencyMs/body`
- 失败：区分“无页面权限”与“无调用授权”

## 3.1 评分评论专项脚本（Agent/Skill/MCP/App）

### 提交评分与评论（成功路径）
1. 用户进入资源详情页 `评价` 标签。
2. 点击 `写评论` 打开弹窗。
3. 选择评分（1-5）并输入评论内容。
4. 点击 `提交`。
5. 调用 `POST /reviews`。
6. 成功后：关闭弹窗，刷新 `GET /reviews` 与 `GET /reviews/summary`，显示最新评论。

### 提交评分与评论（失败重试）
1. 提交时报错（400/401/403/500 任一）。
2. 弹窗保持打开，保留评分和文本输入。
3. 展示错误文案（优先后端 message）。
4. 用户修正后点击 `重试提交`（仍走 `POST /reviews`）。

### 点赞 helpful（成功路径）
1. 用户在评论列表点击 `有帮助`。
2. 调用 `POST /reviews/{id}/helpful`。
3. 成功后仅更新该条 `helpfulCount` 与按钮态。

### 点赞 helpful（失败重试）
1. 请求失败时不回滚整页，只回滚当前按钮 loading。
2. 展示轻提示“操作失败，请重试”。
3. 用户再次点击触发重试。

## 3.2 市场使用专项脚本（详情 -> resolve/invoke）

### 成功路径
1. 点击 `立即使用`。
2. 调用 `POST /catalog/resolve` 拉取可调用配置。
3. 前端组装调用请求并调用 `POST /invoke`。
4. 结果区显示 `traceId/statusCode/latency/body`。

### 失败路径
- `resolve` 失败：停留详情页，展示“解析失败”并提供 `重试解析`。
- `invoke` 失败：
  - `403`：提示“当前 API Key 未授权或 scope 不足”。
  - `429`：提示“请求过于频繁，请稍后重试”。
  - `500`：显示“服务异常，可重试”，保留请求体。

## 3.3 管理操作专项脚本（注册到授权全链路）

### 注册 -> 提审 -> 审核 -> 发布（成功路径）
1. 在 `*-register` 保存草稿。
2. 回到 `*-list` 点击 `提交审核`。
3. 在 `audit-center/*-audit` 点击 `审核通过`。
4. 状态变为 `testing` 后点击 `发布上架`。
5. 在市场页检索到该资源（`published`）。

### 授权 -> 调用 -> 撤销（成功路径）
1. 在 `resource-grant-management` 点击 `新增授权`。
2. 填写 `resourceType/resourceId/granteeApiKeyId/actions/expiresAt`。
3. 保存后调用方 `invoke` 成功。
4. 管理员点击 `撤销授权`。
5. 调用方再次 `invoke` 返回 403。

### 失败重试
- 审核发布前置不满足：禁用发布按钮并提示。
- 授权新增失败：弹窗不关闭，保留已填字段，允许重试。
- 撤销失败：保留记录状态并提示重试。

## 4) 状态与按钮矩阵

| 状态 | 主按钮 | 禁用按钮 | 页面提示 |
|---|---|---|---|
| `draft` | 保存、提交审核、删除 | 撤回、发布、下线 | 草稿不会在市场展示 |
| `pending_review` | 撤回审核、查看进度 | 编辑、删除、发布、下线 | 审核中不可修改内容 |
| `testing` | 发布、下线、版本管理 | 提交审核、编辑、删除 | 测试中不等于已上架 |
| `published` | 下线、版本管理、授权管理 | 提交审核、撤回审核 | 已上架可检索可使用 |
| `deprecated` | 新建版本、查看历史 | 发布、提交审核 | 已下线不可调用 |

## 完整性检查清单

- [x] 生命周期六大按钮流程完整（保存/提审/撤回/通过/发布/下线）
- [x] 授权与调用按钮流程完整（申请/新增授权/撤销/调用）
- [x] 状态机矩阵完整（`draft/pending_review/testing/published/deprecated`）
- [x] 阻断文案已定义
- [x] 评分评论成功/失败脚本完整
- [x] 市场使用成功/失败脚本完整
- [x] 管理操作全链路脚本完整
