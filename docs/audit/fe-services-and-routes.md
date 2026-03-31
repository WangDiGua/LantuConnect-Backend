# 前端 API 封装与路由（快照）

**前端仓库**：`D:\LantuConnect\LantuConnect-Frontend-main`

## API 服务模块（`src/api/services`）

聚合导出见 `index.ts`。当前导出：

`auth`, `agent`, `skill`, `smart-app`, `dataset`, `provider`, `category`, `user-mgmt`, `monitoring`, `system-config`, `user-settings`, `version`（deprecated stub）, `review`, `audit`, `health`, `dashboard`, `tag`, `quota`, `user-activity`, `notification`, `resource-catalog`, `invoke`, `sandbox`, `sdk`, `resource-grant`, `resource-center`, `resource-audit`, `developer-application`, `sensitive-word`, `file-upload`, `developer-stats`

**未从 barrel 导出、但存在文件**：

- `grant-application.service.ts` — 页面通过 **直接路径** `../../api/services/grant-application.service` 引用，功能已接线；建议补 `index.ts` 导出以统一风格。

## 路由模型

- 顶层：`HashRouter`（[App.tsx](file:///D:/LantuConnect/LantuConnect-Frontend-main/src/App.tsx)）。
- 控制台：`/:role/:page(/:id)?`，`role ∈ { admin, user }`（[MainLayout.tsx](file:///D:/LantuConnect/LantuConnect-Frontend-main/src/layouts/MainLayout.tsx)）。
- 侧栏与合法 `page` id：[navigation.ts](file:///D:/LantuConnect/LantuConnect-Frontend-main/src/constants/navigation.ts) + [consoleRoutes.ts](file:///D:/LantuConnect/LantuConnect-Frontend-main/src/constants/consoleRoutes.ts)。

## 命名重定向

`normalizeDeprecatedPage` 将 `agent-versions`、`agent-create` 等旧 page 映射到新页面（如 `agent-register`），避免书签断裂。
