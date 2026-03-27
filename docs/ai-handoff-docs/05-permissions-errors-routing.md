# 05 权限、错误与路由守卫

## 1) 双层权限模型

### 页面权限（RBAC）
- 由 `@RequireRole` / `@RequirePermission` 控制页面与按钮可见性。
- 无权限：菜单隐藏或页面 403（不可“可点全失败”）。

### 调用权限（Scope + Grant）
- 页面可见不代表可调用。
- `resolve/invoke` 需满足：API Key scope + 资源 grant（跨 owner）。

## 2) 状态码与业务码（按后端代码对齐）

### 2.1 HTTP 状态码映射（`GlobalExceptionHandler` 真值）

| HTTP | 后端触发条件 | 前端页面动作 |
|---|---|---|
| `400` | `PARAM_ERROR`、校验异常、`MaxUploadSizeExceededException` | 表单内高亮错误字段，保留输入 |
| `401` | `UNAUTHORIZED`、`TOKEN_EXPIRED`、`REFRESH_TOKEN_INVALID` | 清理登录态并跳转登录（保留回跳） |
| `403` | `FORBIDDEN` | 区分“无页面权限”与“无调用授权（scope/grant）” |
| `404` | `NOT_FOUND`、`NoResourceFoundException` | 显示资源不存在并提供返回列表 |
| `409` | `CONFLICT`、`DUPLICATE_SUBMIT`、`ILLEGAL_STATE_TRANSITION` | 提示状态冲突/重复提交，不自动重试 |
| `429` | `RATE_LIMITED`、`DAILY_QUOTA_EXHAUSTED`、`MONTHLY_QUOTA_EXHAUSTED`、`CIRCUIT_OPEN`、`QUOTA_EXCEEDED` | 频控提示，按钮冷却后允许重试 |
| `500` | 其余业务码/未捕获异常 | 通用异常 + 重试按钮 + traceId（如有） |

> 代码对齐说明 1：当前后端未定义 `410` 的统一返回映射，前端不要把 `410` 当作主流程分支。  
> 代码对齐说明 2：前端错误处理以 `HTTP + code` 组合判定为准，不仅看 HTTP。  
> 代码对齐说明 3：上传超限默认走 `MaxUploadSizeExceededException -> HTTP 400 + code 1008(FILE_SIZE_EXCEEDED)`。

### 2.2 业务码（`ResultCode`）前端处理规范

| 业务码 | 名称 | 推荐页面行为 |
|---|---|---|
| `1001` | `PARAM_ERROR` | 字段级校验提示 |
| `1002` | `UNAUTHORIZED` | 跳登录 |
| `1003` | `FORBIDDEN` | 显示无权限态 |
| `1004` | `NOT_FOUND` | 显示不存在态 |
| `1005` | `CONFLICT` | 弹出冲突提示并刷新当前行状态 |
| `1006` | `DUPLICATE_SUBMIT` | 提示“请勿重复提交” |
| `1007` | `UNSUPPORTED_FILE_TYPE` | 提示可上传类型 |
| `1008` | `FILE_SIZE_EXCEEDED` | 提示文件超限 |
| `2001` | `TOKEN_EXPIRED` | 静默刷新失败则跳登录 |
| `2002` | `REFRESH_TOKEN_INVALID` | 清理会话并跳登录 |
| `2003` | `ACCOUNT_LOCKED` | 展示账号锁定提示 |
| `2004` | `PASSWORD_ERROR` | 密码错误提示 |
| `2005` | `SMS_CODE_ERROR` | 验证码错误提示 |
| `2006` | `SMS_RATE_LIMITED` | 倒计时后可重发 |
| `2007` | `CSRF_FAILED` | 刷新页面重新提交 |
| `2008` | `SESSION_MISMATCH` | 强制重新登录 |
| `2009` | `OLD_PASSWORD_ERROR` | 原密码错误提示 |
| `2010` | `CAPTCHA_ERROR` | 验证码错误并刷新验证码 |
| `3001` | `RATE_LIMITED` | 展示频控提示 |
| `3002` | `DAILY_QUOTA_EXHAUSTED` | 提示今日额度用尽 |
| `3003` | `MONTHLY_QUOTA_EXHAUSTED` | 提示月额度用尽 |
| `3004` | `CIRCUIT_OPEN` | 提示熔断中，稍后重试 |
| `3005` | `QUOTA_EXCEEDED` | 提示配额已耗尽 |
| `4001` | `ILLEGAL_STATE_TRANSITION` | 阻断按钮链路并提示当前状态 |
| `4002` | `REJECT_REASON_REQUIRED` | 驳回弹窗强制填写原因 |
| `4003` | `DUPLICATE_NAME` | 提示名称/编码重复 |
| `4004` | `DUPLICATE_VERSION` | 提示版本号重复 |
| `4005` | `CANNOT_DELETE_PUBLISHED` | 禁止删除已发布资源 |
| `4006` | `DATASET_ACCESS_DENIED` | 数据集访问受限提示 |
| `4007` | `FAVORITE_EXISTS` | 收藏按钮置灰或提示已收藏 |
| `4008` | `CANNOT_REVIEW_OWN` | 禁止评论自己资源 |
| `4009` | `CANNOT_DELETE_SYSTEM_ROLE` | 禁止删除系统角色 |
| `5001` | `INTERNAL_ERROR` | 通用异常提示 |
| `5002` | `EXTERNAL_SERVICE_ERROR` | 下游服务异常提示 |
| `5003` | `TIMEOUT` | 超时提示并可重试 |
| `5004` | `FILE_STORAGE_ERROR` | 上传失败并可重试 |
| `5005` | `MAIL_SEND_ERROR` | 邮件发送失败提示 |
| `5006` | `SMS_SEND_ERROR` | 短信发送失败提示 |

### 2.3 组合判定补充（避免误判）

- 上传失败若 `HTTP=400 + code=1008`：按“文件超限”处理，不归并到通用参数校验文案。
- 即使业务码是 `1007/1008`，若后端经 `BusinessException` 且未命中 `resolveHttpStatus` 的显式分支，HTTP 可能为 `500`；前端仍需优先根据 `code` 给出精确文案。

## 3) 路由守卫与兼容

- `#/admin/{unknown}`、`#/user/{unknown}`：必须回落默认页。
- 无 admin 权限访问 `#/admin/*`：拦截并回 user 默认页。
- 废弃 slug 必须走 `normalizeDeprecatedPage` 重定向。
- `quick-access` 允许 direct-url-only，不强制挂菜单。

## 4) 安全与头部

- 管理接口：`Authorization` + `X-User-Id`
- 调用接口：`X-Api-Key`（`/invoke` 必填）
- 沙箱调用：`X-Sandbox-Token` 必填
- 建议统一注入 `X-Trace-Id`，便于问题追踪

## 5) 状态交互统一规范（空/错/权限/加载）

### 空态（Empty）
- 列表为空时显示引导动作：`新建资源` 或 `去市场探索`。
- 审核为空时显示“当前无待审核项”，并保留筛选器可见。

### 错态（Error）
- 区域级错误优先，不整页白屏。
- 必须提供 `重试` 按钮；重试仅重拉当前区域数据。

### 权限态（Forbidden）
- `403` 页面显示无权限说明，隐藏危险操作按钮。
- 调用型 `403`（scope/grant）提示“可申请授权”，并提供跳转授权中心。

### 加载态（Loading）
- 列表使用骨架屏或行级 loading。
- 提交按钮进入 loading 后禁止重复点击，直到响应返回。

## 6) 评分评论与调用错误文案建议（含业务码）

- 评论提交失败：`评论提交失败，请检查内容后重试`
- helpful 失败：`操作失败，请稍后重试`
- resolve 失败：`资源解析失败，请重试`
- invoke 403：`当前 API Key 未授权或 scope 不足`
- invoke 429：`调用过于频繁，请稍后重试`
- invoke 500：`服务异常，请稍后重试（可附 traceId）`
- invoke 409（`ILLEGAL_STATE_TRANSITION`）：`资源当前状态不允许调用，请先完成发布流程`

## 完整性检查清单

- [x] RBAC 与 scope/grant 双层权限模型完整
- [x] 400/401/403/404/409/429/500 页面行为完整（与后端代码一致）
- [x] 路由守卫（未知路由、admin 拦截、deprecated 重定向）完整
- [x] 请求头安全规则完整
- [x] 空态/错态/权限态/加载态规范完整
