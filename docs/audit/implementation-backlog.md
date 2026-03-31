# Implementation backlog（须落地补齐）

每条建议对应可审计的 PR；完成后在本文件勾选并更新 [findings.md](findings.md)。

## P0

- [x] **Agent 版本管理闭环**  
  - 已删除 [AgentVersionPage.tsx](file:///D:/LantuConnect/LantuConnect-Frontend-main/src/views/agent/AgentVersionPage.tsx) 与 [version.service.ts](file:///D:/LantuConnect/LantuConnect-Frontend-main/src/api/services/version.service.ts)；版本能力以 [resource-center.service.ts](file:///D:/LantuConnect/LantuConnect-Frontend-main/src/api/services/resource-center.service.ts) 为准。

## P1

- [x] **`grantApplicationService` barrel 导出与 import 统一**  
  - [index.ts](file:///D:/LantuConnect/LantuConnect-Frontend-main/src/api/services/index.ts) 已导出；`GrantApplicationListPage` / `MyGrantApplicationsPage` / `GrantApplicationModal` 已改为 `from '../../api/services'`。

- [x] **删除 legacy 审核 UI**  
  - 已删 `AgentAuditList.tsx`、`SkillAuditList.tsx`、`audit.service.ts`。后端 `/audit/agents|skills` 仍可作兼容，必要时后续下线。

## P2

- [ ] **铺完全量 Endpoint 矩阵**  
  - 在 [endpoint-matrix-snapshot.md](endpoint-matrix-snapshot.md) 基础上，为每个方法级映射增加「引用文件:行」列（可脚本从 TS 提取字符串）。

- [ ] **Legacy `/agents` 直调**  
  - 若仍存在直连旧 REST 的代码路径，按 [LegacyApiDeprecationProperties](file:///d:/LantuConnect-Backend-main/LantuConnect-Backend-main/src/main/java/com/lantu/connect/common/config/LegacyApiDeprecationProperties.java) 迁移到 catalog/registry/sdk。

## 本轮前后端审查闭环（2026-03-31）

- [x] **`GET /monitoring/alert-rule-metrics` 前端动态拉取**  
  - [monitoring.service.ts](file:///D:/LantuConnect/LantuConnect-Frontend-main/src/api/services/monitoring.service.ts)、[useMonitoring.ts](file:///D:/LantuConnect/LantuConnect-Frontend-main/src/hooks/queries/useMonitoring.ts)、[AlertRulesPage.tsx](file:///D:/LantuConnect/LantuConnect-Frontend-main/src/views/monitoring/AlertRulesPage.tsx)。

- [x] **`GET /resource-center/resources/{id}/skill-artifact` 下载入口**  
  - [http.ts](file:///D:/LantuConnect/LantuConnect-Frontend-main/src/lib/http.ts) `getBlob`；[resource-center.service.ts](file:///D:/LantuConnect/LantuConnect-Frontend-main/src/api/services/resource-center.service.ts)；资源管理页等。

- [x] **Dataset `GET /providers` 与 Provider 管理列表对齐**  
  - [provider.service.ts](file:///D:/LantuConnect/LantuConnect-Frontend-main/src/api/services/provider.service.ts)（`getById` 分页扫描回退至单页大 pageSize 查找，见实现注释）。
