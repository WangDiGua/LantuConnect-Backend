# 前端改动审查稿（与后端权限 / 策略 / 统计对齐）

> **目的**：把「应用端 / 管理端」信息架构与后端已落地能力，整理成**可评审的前端任务清单**。  
> **范围**：前端仓库默认 `LantuConnect-Frontend`；本文不实施代码，仅供评审优先级与边界。  
> **更新时间**：2026-04-09（已按 Grant 下线真值修订；与 2026-04-03 初稿相比有重大产品变更）

---

## 0. 真值摘要（2026-04-09）

- **`t_resource_invoke_grant`、`t_resource_grant_application` 已删除**；`/resource-grants*`、`/grant-applications*` **不再提供**。消费以 **API Key + scope + 资源 published**（及网关实现）为主，见 `PRODUCT_DEFINITION.md` §4、`docs/api/public-catalog-contract.md`。  
- **`t_resource.access_policy`** 为历史字段（迁移后多为 `open_platform`），**不是** invoke 的独立开关。  
- 下文 **§4、§5** 中若仍出现「Grant 审批 / accessPolicy 三选一驱动短路」等表述，均以本节为准；旧讨论仅供参考。

---

## 1. 背景：后端已就绪、前端待对齐的点

后端已具备：`consumer` 角色、审核 **publish** 权（owner / 同部门 dept_admin / 平台）、**platform-force-deprecate**、`GET /dashboard/owner-resource-stats`、统一目录与 invoke 等。**不再**存在「开发者逐资源授权给他人 Key」的主链路；前端若仍保留 **资源授权管理**、**授权申请审批** 作为主菜单或文案核心，会与接口真值不一致。个人设置下 `GET .../resource-grants` 仅占位空列表。

---

## 2. 信息架构：「应用端 / 管理端」与统一导航

### 2.1 现状（代码侧要点）

- 控制台通过 `UserRoleContext.platformRoleToConsoleRole` 将 `platform_admin` / `dept_admin` 映射为壳层 `admin`，其余为 `user`；路由由 `consoleRoutes` + `MainLayout` 双域侧边栏驱动。
- `ConsoleHomeRedirect` 已按角色纠正「无权限却记住上次管理端路径」等情况。
- **开发者中心**页面仅在用户域路由挂载；旧 `/admin/...` 链接触发重定向。

### 2.2 评审建议（产品 / UX）

| 主题 | 建议方向 |
|------|----------|
| 命名与心理模型 | 避免与后端真实角色混用；见 `docs/frontend-alignment-handbook.md` §2.8。 |
| 减少重复入口 | API Key：个人设置 vs 用户管理——标明受众。 |
| 角色切换 | 与 `canAccessAdminView` 一致；**consumer** 隐藏管理端入口。 |

---

## 3. `consumer` 角色（只读逛市场）

### 3.1 后端语义

- `consumer`：`agent:read`、`skill:read`、`app:view`、`dataset:read`（`mcp` 与 `skill` 共用 `skill:read`）。见 `PRODUCT_DEFINITION.md` §4。

### 3.2 前端缺口

- `PlatformRoleCode` 是否包含 `consumer`；`normalizeRole` 兜底行为。
- `ROLE_PERMISSIONS` 与后端 JSON 逐字符串对齐。

### 3.3 建议改动

1. 增加 `'consumer'`，`platformRoleToConsoleRole` → `user` 壳层，`canAccessAdminView === false`。  
2. 菜单：`consumer` 不展示开发者登记、审核台等入口（按 `hasPermission` 核对）。  
3. 个人中心角色中文名增加 `consumer`。

---

## 4. 资源字段 `accessPolicy`（历史兼容）

### 4.1 后端

- 库字段 `t_resource.access_policy`；新建/迁移后多为 **`open_platform`**；**网关不以本字段做逐资源授权拦截**。OpenAPI 可能仍返回该键，含义见 `ResourceCatalogItemVO` 注释。

### 4.2 前端

- DTO 已有可选 `accessPolicy` 时：仅**只读展示**即可（如「历史：open_platform」），**无需**再配「grant_required / 组织开放」等产品向导 unless 产品明确要求展示库值。  
- 目录项 `hasGrantForKey`：**历史字段名**，不表示存在 Grant 行（见后端 Schema）。

### 4.3 建议

- 市场/详情：可选展示 `accessPolicy` 为信息性标签，辅以/tooltip 指向「调用以 Key+scope+published 为准」。  
- **不要**再写「须先申请资源授权 / Grant 通过才能 invoke」类主叙事。

---

## 5. ~~Grant 授权申请~~（已下线）

**2026-04-09 起**：无 `/grant-applications*`、无资源授权 CRUD。**前端应**：下线或隐藏 `resource-grant-management`、`grant-applications` 等slug 与菜单；若暂留路由，改为说明页指向本文档 §0。  
勿再排期「owner 批 Grant 工单」类 **P0**，除非后端重新引入表与接口（当前无）。

---

## 6. 审核、发布与平台强制下架

### 6.1 后端

- `publish`：owner / 同部门 dept_admin / platform_admin、admin；仅 `审核通过即 published`。
- `POST /audit/resources/{id}/platform-force-deprecate`：仅 platform_admin。

### 6.2 前端建议

1. 审核列表 / 详情：发布按钮与 `allowedActions` 或接口结果对齐。  
2. 平台强制下架：仅管理端、二次确认、`reason` 与 body 一致。  
3. 区分开发者自助 `deprecate` 与平台强制下架文案。

---

## 7. Owner 维度统计 API

### 7.1 后端

- `GET /dashboard/owner-resource-stats?periodDays=&ownerUserId=`：聚合 call_log、usage_record(invoke)、skill_pack_download_event；权限：本人、同 menu_id 的 dept_admin、platform_admin/admin。

### 7.2 前端

- 核对 `developerStatsService` 是否对接该路径；图表字段与响应一致；页内提示「调用量 ≠ 全部资产使用量」（`PRODUCT_DEFINITION.md` §5）。

---

## 8. 其它一致性

| 项 | 📝 |
|----|-----|
| 工作台快捷入口 | **移除**或替换仍指向 `resource-grant-management` 的卡片 unless 改为「API Key / 文档」 |
| Handbook | `docs/frontend-alignment-handbook.md` §2.9 等为真值索引 |
| Swagger | 生产默认关闭 `expose-api-docs` |

---

## 9. 建议实施顺序

1. **P0**：菜单/路由去掉 Grant 主路径；`consumer` 类型与菜单；文案与 `ApiDocsPage` 一致。  
2. **P1**：审核发布与强制下架；`accessPolicy` 若展示则带「历史」说明。  
3. **P2**：`owner-resource-stats`；导航体验收口。  
4. **持续**：Handbook / 市场帮助与后端同步。

---

## 10. 修订记录

| 日期 | 说明 |
|------|------|
| 2026-04-03 | 初稿（含 Grant 工单与 accessPolicy 短路叙事）。 |
| 2026-04-09 | Grant 表与接口下线；全文重写 §0–§5、§8–§9；与 `public-catalog-contract.md` 对齐。 |

---

*审查通过后可拆 issue；接口以 Swagger 与后端 DTO 为准。*
