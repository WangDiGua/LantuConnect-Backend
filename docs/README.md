# 文档目录说明（本仓库 = 后端）

本仓库为 **NexusAI Connect 后端**。`docs/` 下与**运行中的 Java 实现、配置、数据库**一致性的**权威叙述**以 `docs/backend/` 为准。

## 建议阅读顺序（后端）

1. [backend/README.md](backend/README.md) — 总览与索引  
2. [backend/architecture/backend-architecture.md](backend/architecture/backend-architecture.md) — 平台定位与能力边界  
3. [backend/architecture/current-implementation-notes.md](backend/architecture/current-implementation-notes.md) — **与当前代码对齐的实现要点**（存储、标签、版本、技能包校验等）  
4. [backend/architecture/backend-contract-freeze.md](backend/architecture/backend-contract-freeze.md) — 冻结契约与数据模型  
5. [changes/README.md](changes/README.md) — 单点功能改动归档  

## 其他目录（非后端权威或不以本仓维护前端为准）

- `docs/audit/`：**功能闭环与前后端接线审查产物**（Controller 清单、Endpoint 快照、implementation-backlog）；执行审查时以**源码**为准迭代更新。  
- `docs/frontend-backend-handoff/`、`docs/frontend-alignment-playbook/`、`docs/ai-handoff-docs/`：前后端协作、前端对齐与 AI 交接材料；**接口与枚举的「后端事实」请以本仓 Controller/DTO、`sql/` 及 `docs/backend` 为准**。  
- `docs/` 根目录下若存在与 `frontend-*.md`、全站规格相关的副本，多为历史或对外说明用；**不替代** `docs/backend` 中的后端基线。  
- `resource-registration-authorization-invocation-guide.md`、`notification-event-matrix.md`：能力与事件矩阵类说明，更新时请对照 `gateway` 与 `sql/migrations/`。  
