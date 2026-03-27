# API Key Scope 规则说明（Phase 3）

## 1. 适用范围

本规则仅针对统一网关接口：

- `GET /catalog/resources`
- `GET /catalog/resources/{type}/{id}`
- `POST /catalog/resolve`
- `POST /invoke`

## 2. 鉴权头

调用方通过请求头传递：

- `X-Api-Key`

系统会校验：

- Key 是否存在
- Key 状态是否为 `active`
- Key 是否过期

## 3. Scope 语法

支持以下 Scope 语法：

- `*`：全放行
- `{action}:*`：动作级全放行
- `{action}:type:{resourceType}`：按类型放行
- `{action}:id:{resourceType}:{resourceId}`：按单资源放行

动作枚举：

- `catalog`
- `resolve`
- `invoke`

## 4. 兼容规则（历史 scope）

为了平滑迁移，系统保留以下兼容映射：

- `agent:read` 可读取 agent
- `skill:read` 可读取 skill 和 mcp
- `app:view` 可读取 app
- `dataset:read` 可读取 dataset

说明：兼容规则仅用于过渡期，后续建议全部迁移到新 scope 语法。

## 5. 生效策略

- `catalog`：按 scope 裁剪资源列表
- `resolve`：按 scope 校验资源解析权限
- `invoke`：必须通过 scope 校验才可执行

## 6. 计量策略

调用成功进入网关后会更新：

- `last_used_at`
- `call_count`

用于后续审计与配额治理。
