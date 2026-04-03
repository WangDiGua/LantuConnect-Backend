# 当前实现要点（与代码库同步）

本文档描述 **本仓库当前行为**，补充 `backend-architecture.md` 与 `backend-contract-freeze.md` 中未逐项展开的实现细节。若与旧文档冲突，**以本文 + 源码为准**。

## 1. 文件存储（`local` / `minio`）

- 配置：`application.yml` 中 `file.storage-type` 为 `local` 或 `minio`（默认 `local`）。上传写入由 **`FileStorageService`** 严格二选一（本地 `/uploads/...` 或 `endpoint/bucket/key` 形态的 URL 前缀）。  
- 共享能力：[`FileStorageSupport`](../../../src/main/java/com/lantu/connect/common/storage/FileStorageSupport.java) 负责本地路径解析、MinIO 客户端与 object key 提取。  
- **技能制品下载**：[`SkillArtifactDownloadService`](../../../src/main/java/com/lantu/connect/gateway/service/SkillArtifactDownloadService.java) **与 `file.storage-type` 一致**：`local` 时只读 `artifact_uri` 以 `/uploads/` 解析到 `file.upload-dir` 下的文件；`minio` 时仅从与 `file.minio.endpoint`、`bucket` 匹配前缀的 URI 读对象；**不再**在两种后端之间自动回退。URI 与当前模式不符时会返回明确业务错误。  
- **切换存储后的历史数据**：`t_resource_skill_ext.artifact_uri` 须与当前 `storage-type` 形态一致；迁移步骤见 [`migration-notes/storage-type-switch-artifact-uri.md`](../migration-notes/storage-type-switch-artifact-uri.md)。

## 2. 资源标签与目录

- **目录标签（可筛选）**：`t_resource.category_id`（`t_tag.id`）+ **`t_resource_tag_rel`**（`resource_type`、`resource_id`、`tag_id`）。资源 **create/update** 时同步 rel；**软删** 时删除该资源在 rel 表中的行。  
- **数据集 JSON 标签**：`t_resource_dataset_ext.tags` 为自由文案数组；仅当某字符串与 **`t_tag.name` 精确匹配且唯一** 时额外写入 `t_resource_tag_rel`；**不会**自动新建 `t_tag`。  
- **目录列表**：`GET /catalog/resources` 的 query `tags` 按标签名过滤（依赖 rel + `t_tag`）；列表项 VO 带 **`tags`**（名字符串列表）。  
- **管理端详情/列表**：`ResourceManageVO` 使用 **`catalogTagNames`** 表示 rel 侧目录标签；**`tags` 字段仅表示数据集 JSON**（与目录筛选解耦）。

## 3. 资源版本

- 列表与详情会填充 **`currentVersion`**（当前生效版本展示名/标签，依服务实现而定）。  
- 版本 VO 使用 **`isCurrent`**（JSON），并接受别名 `current` 反序列化以便兼容旧客户端。

## 4. 技能扩展与包校验

- 表 **`t_resource_skill_ext`** 含技能包校验与技能根路径列：`pack_validation_status`、`pack_validated_at`、`pack_validation_message`、`skill_root_path` 等；历史库若缺列，依次参见 [`sql/migrations/20260401_skill_pack_validation.sql`](../../../sql/migrations/20260401_skill_pack_validation.sql)、[`sql/migrations/20260402_skill_skill_root_path.sql`](../../../sql/migrations/20260402_skill_skill_root_path.sql)（或 Flyway `V4`/`V5`）。

## 5. 运行与健康检查

- 管理端口与上下文路径见 `application.yml`（如 `server.port`、`server.servlet.context-path`）。  
- **Actuator 聚合健康**：若 MySQL、Redis 等依赖未就绪，`/regis/actuator/health`（视 `server.servlet.context-path`）可能整体为 **503**，属预期；排查时可看各 `HealthIndicator` 明细。

## 6. 相关源码入口（便于检索）

| 主题 | 主要类 |
|------|--------|
| 目录网关 | `UnifiedGatewayServiceImpl` |
| 资源注册/版本/标签同步 | `ResourceRegistryServiceImpl` |
| 存储 | `FileStorageService`, `FileStorageSupport` |
| 技能制品下载 | `SkillArtifactDownloadService` |
