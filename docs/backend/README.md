# 后端文档总览（最终架构）

## 1. 阅读入口

建议按以下顺序阅读：

1. `architecture/backend-architecture.md`（平台定位与总体能力边界）
2. `architecture/current-implementation-notes.md`（**与当前代码一致**：存储 local/minio、标签 rel、版本字段、技能包校验列等）
3. `architecture/backend-contract-freeze.md`（冻结契约、状态机与数据模型）
4. `architecture/backend-directory-layout-final.md`（最终目录结构与收口说明）

## 2. 能力与接入规范

- `api/api-scope-rulebook.md`（Scope 规则）
- `api/dual-authz-enforcement.md`（角色 + API Key 双层鉴权）
- `api/sdk-openapi-and-sandbox.md`（SDK 稳定接口与沙箱）

## 3. 迁移与演进记录

- `migration-notes/storage-type-switch-artifact-uri.md`（切换 local / MinIO 时技能 `artifact_uri` 对齐）
- `migration/resource-core-migration.md`（资源主表 + 扩展表迁移）
- `migration/monitoring-resource-migration.md`（治理表迁移）
- `migration/gap-closure-final.md`（缺口补全与最终态收敛）
- `migration/cutover-and-rollback-drill.md`（切换/回滚演练历史与最终原则）

## 4. 下线与清理记录

- `deprecation/api-deprecation-and-removal-plan.md`
- `deprecation/legacy-api-inventory.md`
- `deprecation/legacy-api-physical-deletion-checklist.md`
- `deprecation/removed-code-audit.md`

## 5. 维护约束

- 发生后端目录结构调整时，必须同步更新：
  - `architecture/backend-directory-layout-final.md`
  - `deprecation/removed-code-audit.md`
  - 本文档（总览与阅读顺序）

## 6. 与变更归档文档的关系

- `docs/changes` 目录用于阶段性功能改动记录，不作为后端最终架构权威来源。
- 如需查看历史单点改动，请先读：
  - `docs/changes/README.md`
