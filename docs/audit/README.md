# 全系统功能闭环审查产物

执行日期：2026-03-31（首轮自动化枚举 + 抽样交叉验证）。

**原则**（见计划）：以**前后端源码**为准；文档仅辅助。未闭环项见 [implementation-backlog.md](implementation-backlog.md)。

| 文件 | 说明 |
|------|------|
| [controller-inventory.md](controller-inventory.md) | 后端 Controller 与 Servlet 路径前缀（不含 `context-path` `/api`） |
| [endpoint-matrix-snapshot.md](endpoint-matrix-snapshot.md) | 按模块节选的 Endpoint × 前端 service 对照快照 |
| [fe-services-and-routes.md](fe-services-and-routes.md) | 前端 API 封装与路由模型说明 |
| [findings.md](findings.md) | 分级问题摘要 |
| [implementation-backlog.md](implementation-backlog.md) | 须落地的代码补齐项（可拆 PR） |

后续迭代：每新增 Controller 或前端 service，先更新 `controller-inventory.md` 与矩阵对应行。
