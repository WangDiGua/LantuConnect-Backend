# Endpoint × 前端对照快照（按模块）

**图例**：`OK` 已从 `src/api` 找到对应封装并在视图中使用（抽样 grep）；`STUB` 前端为占位/空实现；`LEGACY` 走后端弃用路径；空白表示抽样未完成，须执行下一轮全量 grep。

## 认证与文件

| Endpoint | 前端 service |
|----------|----------------|
| `/auth/*` | `auth.service` |
| `/captcha/*` | 登录页等直拉（可搜 `captcha`） |
| `/files/upload` | `file-upload.service` |

## 目录 / 调用 / SDK / 沙箱

| Endpoint | 前端 service |
|----------|----------------|
| `/catalog/resources` 等 | `resource-catalog.service` |
| `/invoke`, `/invoke-stream` | `invoke.service` + `gatewayInvokeStream` |
| `/sdk/v1/*` | `sdk.service` |
| `/sandbox/*` | `sandbox.service` |
| `/catalog/apps/launch` | 通常后端重定向，前端 launch 流需与 app 资源对齐 |

## 资源注册中心

| Endpoint | 前端 service |
|----------|----------------|
| `/resource-center/resources/*` | `resource-center.service` |
| `.../skills/package-upload`, `package-import-url` | `resource-center.service` |
| `.../skill-artifact` | 下载流，前端需与详情页对齐 |

## 授权与申请

| Endpoint | 前端 service |
|----------|----------------|
| `/resource-grants` | `resource-grant.service` |
| `/grant-applications` | `grant-application.service`（**未进 index.ts**） |

## 审核

| Endpoint | 前端 service | 备注 |
|----------|----------------|------|
| `/audit/resources` 等 | `resource-audit.service` / `audit.service` | 需核对是否仍调 `/audit/agents` 等 **LEGACY** |

## 用户与入驻

| Endpoint | 前端 service |
|----------|----------------|
| `/user-mgmt/*` | `user-mgmt.service` |
| `/developer/applications` | `developer-application.service` |
| `/developer/my-statistics` | `developer-stats.service` |

## 用户侧活动与设置

| Endpoint | 前端 service |
|----------|----------------|
| `/user/*` | `user-activity.service` |
| `/user-settings/*` | `user-settings.service` |
| `/notifications/*` | `notification.service` |
| `/reviews/*` | `review.service` |

## 仪表盘

| Endpoint | 前端 service |
|----------|----------------|
| `/dashboard/*` | `dashboard.service` |

## 监控与健康

| Endpoint | 前端 service |
|----------|----------------|
| `/monitoring/*` | `monitoring.service` |
| `/health/*` | `health.service` |

## 系统配置与数据

| Endpoint | 前端 service |
|----------|----------------|
| `/system-config/*` | `system-config.service`（含 model/rate-limit/announcement 等子路径） |
| `/quotas`, `/rate-limits` | `quota.service` |
| `/tags` | `tag.service` |
| `/providers`, `/dataset/providers` | `provider.service` |
| `/sensitive-words` | `sensitive-word.service` |

## 前端「资源类型」适配层

| 模块 | 行为 |
|------|------|
| `agent.service` | 主要 **委托** `resource-catalog`，非直连 `/agents` |
| `skill.service` / `smart-app` / `dataset` | 类似模式（以各文件为准） |
