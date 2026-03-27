# 前端对齐手册差异报告（2026-03-26）

> 对照基准：`docs/frontend-alignment-handbook.md`（更新时间 2026-03-24）  
> 真值来源：后端代码当前状态（2026-03-26 最新提交）  
> 结论：手册存在 **12 处** 实质性差异，需同步更新。

---

## 差异总览

| # | 差异类型 | 涉及手册章节 | 严重度 | 说明 |
|---|---------|------------|--------|------|
| 1 | 新增控制器 | §4（缺失） | **高** | `GrantApplicationController`（`/grant-applications`）整体缺失，前端无法对接授权申请工单 |
| 2 | 权限变更 | §4.11 审核接口 | **高** | `publish` 端点权限从 `platform_admin/dept_admin` 收紧为仅 `platform_admin` |
| 3 | 参数变更 | §4.11 审核接口 | **高** | 所有审核列表/操作端点新增 `X-User-Id` 必填 header；列表新增 `status` 查询参数 |
| 4 | 参数变更 | §4.10 Dashboard | **中** | `GET /dashboard/admin-overview` 新增 `X-User-Id` 必填 header |
| 5 | 字段新增 | §5.3.1 ResourceUpsertRequest | **中** | 新增 `relatedResourceIds` 字段（Agent/App 资源关联） |
| 6 | 错误码新增 | §2.7 错误映射 | **中** | 新增 3 个 ResultCode：4010/4011/4012 |
| 7 | 控制器计数 | §4、07-controller-coverage-matrix | **中** | 控制器总数从 27 变为 28 |
| 8 | 配置变更 | 全文未提及 | **低** | 新增 RabbitMQ / MinIO / HTTPS / 加密 / 多通道通知配置 |
| 9 | 新增能力 | 全文未提及 | **低** | 多通道通知（邮件/短信）、消息队列事件、字段加密工具 |
| 10 | 流程变更 | §5.6（流程图） | **中** | 缺少"授权申请→审批"流程图 |
| 11 | 两级审核 | §2.8、§4.11 | **高** | 手册未体现两级审核模型（dept_admin 审核 → platform_admin 发布）|
| 12 | 部门隔离 | §2.8 | **中** | dept_admin 审核列表/资源列表/Dashboard 已按部门 menuId 过滤，手册未说明 |

---

## 差异详情

### 差异 1：新增 `GrantApplicationController`（授权申请工单）

**手册现状：** 完全缺失，手册只记录了 `/resource-grants` 的直接授权模式。

**代码真值：** 新增 `GrantApplicationController`，路由前缀 `/grant-applications`。

| 方法 | 路径 | 鉴权/权限 | 请求要点 | 返回 |
|------|------|----------|---------|------|
| POST | `/grant-applications` | `X-User-Id` + `@AuditLog` | body: `GrantApplicationRequest` | `R<Map<String,Long>>` (applicationId) |
| GET | `/grant-applications/mine` | `X-User-Id` | query: `status?`, `page`, `pageSize` | `R<PageResult<GrantApplicationVO>>` |
| GET | `/grant-applications/pending` | `@RequireRole({"platform_admin"})` | query: `status?`, `page`, `pageSize` | `R<PageResult<GrantApplicationVO>>` |
| POST | `/grant-applications/{id}/approve` | `@RequireRole({"platform_admin"})` + `X-User-Id` + `@AuditLog` | path: `id` | `R<Void>` |
| POST | `/grant-applications/{id}/reject` | `@RequireRole({"platform_admin"})` + `X-User-Id` + `@AuditLog` | path: `id`; body: `ResourceRejectRequest` | `R<Void>` |

**GrantApplicationRequest 字段：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `resourceType` | String | 是 | 资源类型 |
| `resourceId` | Long | 是 | 目标资源 ID |
| `apiKeyId` | String | 是 | 申请绑定的 API Key |
| `actions` | List\<String\> | 是 | 申请的操作权限 |
| `useCase` | String | 否 | 使用场景说明 |
| `callFrequency` | String | 否 | 预估调用频次 |
| `expiresAt` | LocalDateTime | 否 | 申请的授权过期时间 |

**GrantApplicationVO 字段：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | Long | 申请 ID |
| `applicantId` | Long | 申请人 |
| `resourceType` | String | 资源类型 |
| `resourceId` | Long | 资源 ID |
| `apiKeyId` | String | API Key |
| `actions` | List\<String\> | 操作权限 |
| `useCase` | String | 使用场景 |
| `callFrequency` | String | 调用频次 |
| `status` | String | `pending/approved/rejected` |
| `reviewerId` | Long | 审批人 |
| `rejectReason` | String | 驳回原因 |
| `reviewTime` | LocalDateTime | 审批时间 |
| `expiresAt` | LocalDateTime | 过期时间 |
| `createTime` | LocalDateTime | 创建时间 |

**需要在手册中补充的位置：**
- §4 新增 §4.2.3 授权申请工单接口
- §3 逐页面对齐新增 `grant-application-list`（admin slug）
- §5.6 新增 §5.6.5 授权申请→审批流程图
- `01-menu-and-page-blueprints.md` 补充 admin 页面
- `07-controller-coverage-matrix.md` 新增一行

---

### 差异 2：审核 `publish` 端点权限收紧

**手册现状（§4.11）：**
```
POST /audit/agents/{id}/publish    @RequireRole(platform_admin/dept_admin)
POST /audit/skills/{id}/publish    @RequireRole(platform_admin/dept_admin)
POST /audit/resources/{id}/publish @RequireRole(platform_admin/dept_admin)
```

**代码真值：**
```
POST /audit/agents/{id}/publish    @RequireRole({"platform_admin"})     ← 仅 platform_admin
POST /audit/skills/{id}/publish    @RequireRole({"platform_admin"})     ← 仅 platform_admin
POST /audit/resources/{id}/publish @RequireRole({"platform_admin"})     ← 仅 platform_admin
```

**影响：** 前端审核页面中，dept_admin 角色不应显示"发布"按钮；仅 platform_admin 可执行发布操作。这是两级审核模型的核心约束。

---

### 差异 3：审核接口参数变更

**手册现状（§4.11）：**
```
GET  /audit/agents        query: page, pageSize
GET  /audit/skills        query: page, pageSize
GET  /audit/resources     query: resourceType?, page, pageSize
POST /audit/agents/{id}/approve   @RequireRole(platform_admin/dept_admin)
POST /audit/agents/{id}/reject    @RequireRole(platform_admin/dept_admin) + body:RejectBody
```

**代码真值：**
```
GET  /audit/agents        header: X-User-Id; query: page, pageSize                    ← 新增 X-User-Id
GET  /audit/skills        header: X-User-Id; query: page, pageSize                    ← 新增 X-User-Id
GET  /audit/resources     header: X-User-Id; query: resourceType?, status?, page, pageSize  ← 新增 X-User-Id + status
POST /audit/agents/{id}/approve   header: X-User-Id; @RequireRole(platform_admin/dept_admin)  ← 新增 X-User-Id
POST /audit/agents/{id}/reject    header: X-User-Id; @RequireRole(platform_admin/dept_admin) + body  ← 新增 X-User-Id
```

**影响：**
- 所有审核接口现在需要传 `X-User-Id` header
- 列表接口支持按 `status` 过滤（可查 `pending_review` / `testing` / `published` / `rejected` 等状态的审核项）
- dept_admin 调用列表接口时，后端自动按部门 menuId 过滤，只能看到本部门提交的审核项
- approve/reject/publish 现在会记录 `reviewerId`

---

### 差异 4：Dashboard admin-overview 参数变更

**手册现状（§4.10）：**
```
GET /dashboard/admin-overview    @RequirePermission(monitor:view)
```

**代码真值：**
```
GET /dashboard/admin-overview    @RequirePermission(monitor:view) + header: X-User-Id   ← 新增
```

**影响：** dept_admin 调用时返回的数据会按部门维度过滤（仅本部门的资源统计和用户统计）。前端需要确保传递 `X-User-Id`。

---

### 差异 5：ResourceUpsertRequest 新增 `relatedResourceIds` 字段

**手册现状（§5.3.1）：** 未列出 `relatedResourceIds` 字段。

**代码真值：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `relatedResourceIds` | List\<Long\> | 否 | 关联资源 ID 列表（Agent 依赖的 Skills、App 依赖的 Agent/Skills 等） |

**行为说明：**
- 仅对 `agent` 和 `app` 类型生效
- `agent` → 写入 `t_resource_relation`，relation_type = `agent_depends_skill`
- `app` → 写入 `t_resource_relation`，relation_type = `app_depends_resource`
- create 和 update 时均生效（update 会先删除旧关联再重建）

---

### 差异 6：新增 ResultCode 错误码

**手册现状（§2.7）：** 未列出以下错误码。

**代码真值：新增 3 个错误码**

| 码值 | 常量名 | 消息 | 触发场景 |
|------|--------|------|---------|
| 4010 | `GRANT_APPLICATION_DUPLICATE` | 已有待审批的授权申请 | 重复提交授权申请 |
| 4011 | `GRANT_APPLICATION_NOT_FOUND` | 授权申请不存在 | 审批不存在的申请 |
| 4012 | `GRANT_APPLICATION_NOT_PENDING` | 授权申请不在待审批状态 | 审批已处理的申请 |

**手册 §2.7 需补充的错误映射行：**

| 场景 | HTTP/业务码 | 前端动作 |
|------|-----------|---------|
| 授权申请重复 | `4010` | 提示"已有待审批的授权申请" |
| 授权申请不存在 | `4011` | 提示申请不存在 |
| 授权申请已处理 | `4012` | 提示申请已处理，刷新列表 |

---

### 差异 7：Controller 总数变更

**手册现状：** §4 和 `07-controller-coverage-matrix.md` 记录 27 个 Controller。

**代码真值：** 28 个 Controller（新增 `GrantApplicationController`）。

**`07-controller-coverage-matrix.md` 需追加：**

| Controller | 路由前缀 | 对应文档 |
|------------|---------|---------|
| `GrantApplicationController` | `/grant-applications` | `02`、`03` |

---

### 差异 8：新增基础设施配置

**手册未提及以下 `application.yml` 配置项（代码已生效）：**

| 配置路径 | 默认值 | 说明 |
|---------|--------|------|
| `spring.rabbitmq.host` | `localhost` | RabbitMQ 消息队列（条件激活） |
| `spring.rabbitmq.port` | `5672` | |
| `spring.rabbitmq.username` | `guest` | |
| `spring.rabbitmq.password` | `guest` | |
| `spring.rabbitmq.virtual-host` | `/` | |
| `file.storage-type` | `local` | 文件存储策略：`local` 或 `minio` |
| `file.minio.endpoint` | `http://localhost:9000` | MinIO 端点 |
| `file.minio.access-key` | `minioadmin` | MinIO 密钥 |
| `file.minio.secret-key` | `minioadmin` | MinIO 密钥 |
| `file.minio.bucket` | `lantu-connect` | MinIO 桶名 |
| `lantu.security.require-https` | `false` | 生产环境 HTTPS 强制 |
| `lantu.security.encryption-key` | (空) | AES-256-GCM 字段加密密钥 |
| `lantu.notification.sms-enabled` | `false` | 短信通知开关 |
| `lantu.notification.email-enabled` | `false` | 邮件通知开关 |

**前端影响：** 对前端无直接影响（均为后端配置），但 MinIO 模式下文件上传返回的 URL 格式会变化（从 `/uploads/...` 变为 `http://minio:9000/lantu-connect/...`），前端渲染文件/图片时需注意。

---

### 差异 9：新增后端能力（未在手册中体现）

| 能力 | 类/接口 | 说明 |
|------|---------|------|
| 多通道通知 | `MultiChannelNotificationService` | 同时发送站内通知 + 邮件 + 短信 |
| 邮件通知 | `EmailNotificationChannel` | 条件激活：`spring.mail.host` 配置时 |
| 短信通知 | `SmsNotificationChannel` | 条件激活：`lantu.notification.sms-enabled=true` |
| 消息队列事件 | `EventPublisher` + `PlatformEvent` | RabbitMQ 异步事件发布 |
| 字段加密 | `FieldEncryptor` | AES-256-GCM 字段级加密/解密 |
| HTTPS 强制 | `SecurityConfig` + `SecurityProperties.requireHttps` | 生产环境可强制 HTTPS |

**前端影响：** 无直接接口变化，但用户可能通过邮件/短信收到通知。

---

### 差异 10：缺少授权申请流程图

**手册现状（§5.6）：** 有 4 个流程图（登录刷新、入驻、统一调用、授权他人调用），但缺少"授权申请→审批"流程。

**应补充的流程：**

```
开发者 → POST /grant-applications（提交申请）
       → GET /grant-applications/mine（查看我的申请）
平台管理员 → GET /grant-applications/pending（查看待审批）
           → POST /grant-applications/{id}/approve（通过 → 自动创建 grant）
           → POST /grant-applications/{id}/reject（驳回）
```

---

### 差异 11：两级审核模型未在手册中体现

**手册现状（§2.8）：** 描述了角色模型，但未说明审核流程的两级分离。

**代码真值：**
- **第一级（部门审核）：** `dept_admin` 执行 `approve`（pending_review → testing）或 `reject`
- **第二级（平台发布）：** `platform_admin` 执行 `publish`（testing → published）
- `dept_admin` 调用审核列表时，只能看到本部门提交的审核项（按 submitter 的 menuId 过滤）
- 审核通过/驳回/发布操作均自动向资源提交者发送站内通知

**手册需在 §2.8 或 §4.11 补充两级审核说明。**

---

### 差异 12：部门数据隔离未在手册中说明

**手册现状：** 未提及 dept_admin 的数据隔离范围。

**代码真值：**
- `GET /audit/resources` — dept_admin 只能看到本部门用户提交的审核项
- `GET /resource-center/resources/mine` — dept_admin 只能看到本部门用户创建的资源
- `GET /dashboard/admin-overview` — dept_admin 看到的统计数据仅限本部门

**手册需在 §2.8 补充 dept_admin 数据隔离规则。**

---

## 手册最新版应当包含的修订（摘要）

### §2.7 错误映射表 — 追加 3 行

```
| 授权申请重复        | 4010         | 提示"已有待审批的授权申请"        |
| 授权申请不存在       | 4011         | 提示申请不存在                  |
| 授权申请已处理       | 4012         | 提示申请已处理，刷新列表           |
```

### §2.8 角色模型 — 补充两级审核与部门隔离

在 §2.8.2 后追加：

> **两级审核模型：**
> - `dept_admin`：可执行 `approve`（通过）和 `reject`（驳回），对应部门审核阶段
> - `platform_admin`：可执行 `publish`（发布上线），对应平台上线阶段
> - `dept_admin` 审核列表自动按部门 menuId 过滤，仅可见本部门资源
>
> **部门数据隔离：**
> - `dept_admin` 的资源列表（`/resource-center/resources/mine`）按部门 menuId 过滤
> - `dept_admin` 的管理概览（`/dashboard/admin-overview`）仅统计本部门数据

### §4.2.3 — 新增"授权申请工单"

完整内容见本文差异 1。

### §4.10 — 修订 admin-overview

```diff
- GET /dashboard/admin-overview    @RequirePermission(monitor:view)
+ GET /dashboard/admin-overview    @RequirePermission(monitor:view) + header: X-User-Id
```

### §4.11 — 修订审核接口

所有审核端点追加 `X-User-Id` header。  
列表端点追加 `status` 查询参数。  
`publish` 端点权限修订为仅 `@RequireRole({"platform_admin"})`。

### §5.3.1 — ResourceUpsertRequest 追加字段

```
| relatedResourceIds | List<Long> | 否 | 关联资源 ID 列表（Agent/App 资源关联） |
```

### §5.6 — 新增授权申请流程图

见本文差异 10。

### 07-controller-coverage-matrix.md — 追加 GrantApplicationController

```
| GrantApplicationController | /grant-applications | 02、03 |
```

总 Controller 数更新为 **28**。

---

## 无差异确认（以下部分与手册一致）

- §4.1 Auth + Captcha — 无变化
- §4.2 统一资源目录与调用 — 无变化
- §4.2.1 资源调用授权管理 — 无变化
- §4.2.2 统一资源注册中心 — 无变化（除 relatedResourceIds 外）
- §4.3 SDK v1 — 无变化
- §4.4 沙箱 — 无变化
- §4.5 开发者入驻 — 无变化
- §4.6 用户设置 — 无变化
- §4.7 用户活动 — 无变化
- §4.8 用户管理 — 无变化
- §4.9 系统配置 + 配额限流 — 无变化
- §5.1-§5.5 核心接口字段级契约 — 无变化（除 ResourceUpsertRequest 外）
- §7 旧接口下线附录 — 无变化
- 认证/鉴权链路、Header 契约、响应/分页契约 — 无变化
- `permissions.yml` — 无变化（4 个角色 39 个权限点）
