# 前端功能核对文档（入口索引）

> 原超长版本已按“可执行改造逻辑”拆分到目录：`docs/frontend-alignment-playbook/`。  
> 本文件保留为入口页，供前端与后端统一跳转。

## 1. 使用目标

- 用一套后端驱动文档完成前端目录、页面、按钮、接口、权限、异常的对齐改造。
- 避免在单一超长文件中反复滚动与冲突编辑。

## 2. 文档入口

- [README](./frontend-alignment-playbook/README.md)
- [01 菜单与页面蓝图](./frontend-alignment-playbook/01-menu-and-page-blueprints.md)
- [02 接口到页面映射总表](./frontend-alignment-playbook/02-api-to-page-mapping.md)
- [03 按钮流程与状态机](./frontend-alignment-playbook/03-button-flows-and-state-machine.md)
- [04 查询参数与字段展示映射](./frontend-alignment-playbook/04-query-fields-and-display-mapping.md)
- [05 权限、错误与路由守卫](./frontend-alignment-playbook/05-permissions-errors-routing.md)
- [06 改造顺序与验收](./frontend-alignment-playbook/06-rollout-and-acceptance.md)
- [07 Controller 覆盖矩阵](./frontend-alignment-playbook/07-controller-coverage-matrix.md)
- [08 全量覆盖审计矩阵](./frontend-alignment-playbook/08-coverage-audit-matrix.md)

## 3. 对齐参考文档

- `docs/frontend-backend-alignment-spec.md`
- `docs/frontend-full-spec.md`
- `docs/resource-registration-authorization-invocation-guide.md`
- `docs/frontend-resource-registration-runbook.md`

## 4. 协作规则

- 页面改造：先更新 `01` + `02`，再补 `03/04/05`。
- 接口变更：先更新 `02` + `07`，再补页面蓝图与验收路径。
- 联调排障：优先看 `05`（权限/错误/路由）和 `06`（回归路径）。
