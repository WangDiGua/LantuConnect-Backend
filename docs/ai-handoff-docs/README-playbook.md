# 前端对齐改造手册（拆分版）

> 目的：把原 `frontend-role-page-functional-checklist.md` 的超长内容拆成可执行文档包，方便前端按顺序改造。  
> 适用角色：`platform_admin`、`dept_admin`、`developer`。  
> 后端口径：以 Controller 真值为准，接口路径相对 `/api`。

## 阅读顺序（建议，含 UI 设计）

1. `01-menu-and-page-blueprints.md`（含页面分区蓝图 + 低保真线框 + 组件规范）  
2. `02-api-to-page-mapping.md`  
3. `03-button-flows-and-state-machine.md`（含成功/失败重试脚本）  
4. `04-query-fields-and-display-mapping.md`（含字段落位图）  
5. `05-permissions-errors-routing.md`（含空态/错态/权限态/加载态）  
6. `07-controller-coverage-matrix.md`  
7. `08-coverage-audit-matrix.md`  
8. `06-rollout-and-acceptance.md`

## 文档清单

- `01-menu-and-page-blueprints.md`：目录、菜单、页面结构蓝图（全页面，不只关键页）+ 低保真线框 + 组件规范。
- `02-api-to-page-mapping.md`：后端接口到前端页面/功能映射总表（含 Controller 端点附录）。
- `03-button-flows-and-state-machine.md`：每类按钮流程与状态机阻断规范。
- `04-query-fields-and-display-mapping.md`：列表查询参数与返回字段展示建议。
- `05-permissions-errors-routing.md`：权限模型、错误码处理、路由守卫（含 HTTP/业务码与后端代码对齐表）。
- `06-rollout-and-acceptance.md`：改造顺序、验收路径、回归清单。
- `07-controller-coverage-matrix.md`：Controller 全量覆盖矩阵（审计用）。
- `08-coverage-audit-matrix.md`：按文档维度的全量覆盖审计结果。

## 全量检查结果（本轮）

| 项目 | 结果 |
|---|---|
| Controller 覆盖 | 27/27 |
| 主链路接口映射 | 已完成 |
| 全页面路由映射 | 已完成 |
| 状态码与业务码对齐 | 已完成（按代码真值） |
| 列表查询/字段映射 | 已完成 |
| 按钮流程/状态机 | 已完成 |
| 权限/错误/路由守卫 | 已完成 |
| 审计矩阵 | 已完成（见 `08`） |

最后核对日期：2026-03-25

## 维护规则

- 新增接口：先更新 `02` 与 `07`，再补 `03/04/05`。
- 新增页面：先更新 `01`，再补 `02/03/04`。
- 状态机变更：必须同时更新 `03` 和 `06`。
