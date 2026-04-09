# 前端 ↔ 后端协作文档（集中目录）

所有与**接口、检索参数、枚举对齐、管理端接线**相关的说明统一放在本目录，避免散落在 `docs/` 根目录。

| 文档 | 用途 |
|------|------|
| [README.md](./README.md) | 本索引 |
| [**01-backend-api-and-query-checklist.md**](./01-backend-api-and-query-checklist.md) | **发给后端**：需对照的前端源文件清单 + 按接口整理的 query/能力缺口 |
| [**02-dropdown-enums-alignment.md**](./02-dropdown-enums-alignment.md) | **全站下拉/筛选**：前端已有枚举字面量表 + 需后端字典/补充项 + 动态下拉（API 拉取） |
| [**03-backend-enum-and-api-contract.md**](./03-backend-enum-and-api-contract.md) | **后端核对用**：从 02 浓缩的枚举表 + 本仓库字典接口 + query 别名与路径备忘 |
| [frontend-completion-before-backend.md](./frontend-completion-before-backend.md) | 管理端「先补前端再补后端」分工与敏感词等说明 |
| [frontend-feature-gap-matrix.md](./frontend-feature-gap-matrix.md) | 路由 × 数据流 × 筛选形态矩阵 |
| [frontend-management-ui-audit-and-backend-api-requests.md](./frontend-management-ui-audit-and-backend-api-requests.md) | 管理端 UI 巡检 + 分接口的后端检索建议 |

**维护约定**：接线或枚举有变更时，优先更新 `01`、`02` 与 `frontend-feature-gap-matrix.md` 对应行，并在审计文档顶部「前端接线状态」补一行日期说明。

**最近后端对齐**：**2026-03-30** 起监控列表 query、审计 `result`、访问令牌分页与撤销等；**2026-04-09** 起 **Grant / grant-applications / `/resource-grants*` 已删除**（见 `PRODUCT_DEFINITION.md` §4）。另含 **`GET /rate-limits` keyword**、**敏感词 PUT `word`**、**ACL**、**Provider**、告警 query 等 — 见 `01`、`03-backend-enum-and-api-contract.md` 与 `frontend-feature-gap-matrix`。
