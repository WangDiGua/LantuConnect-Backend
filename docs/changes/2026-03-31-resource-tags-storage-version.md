# 2026-03-31 资源标签、存储与版本字段对齐

## 背景

对齐目录筛选、管理端展示与客户端兼容，并统一文件存储读写语义。

## 行为摘要

1. **标签**  
   - `create/update` 同步 `t_resource_tag_rel`；软删清理 rel。  
   - 目录列表 `GET /catalog/resources` 返回项含 `tags`（名列表）；query `tags` 按 rel 过滤。  
   - `ResourceManageVO.catalogTagNames` 为目录标签；`tags` 仅为数据集 JSON。

2. **存储**  
   - `file.storage-type`：`local` | `minio`。  
   - `SkillArtifactDownloadService` 读路径：本地文件优先，否则 MinIO key（与当前 `storage-type` 解耦）。

3. **版本**  
   - 列表/详情补充 `currentVersion`；`ResourceVersionVO` 使用 `isCurrent`，并支持 `current` 别名入参。

4. **数据库**  
   - `t_resource_skill_ext` 技能包校验列：缺列环境执行 `sql/migrations/20260401_skill_pack_validation.sql`。

## 权威说明位置

- 实现细节：`docs/backend/architecture/current-implementation-notes.md`  
- 接口与字段备忘：`docs/frontend-backend-handoff/03-backend-enum-and-api-contract.md`（后端核对用）
