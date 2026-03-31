# 切换 file.storage-type 与技能 artifact_uri

## 行为说明

- `file.storage-type` 为 **local** 时，`t_resource_skill_ext.artifact_uri` 应为 **`/uploads/...`**（相对站点根的路径语义，物理文件在 `file.upload-dir` 下）。
- 为 **minio** 时，`artifact_uri` 应为 **`{file.minio.endpoint 去尾斜杠}/{bucket}/{objectKey}`**，与上传成功时返回的前缀一致。

切换模式后，**旧 URI 不会自动改写**；下载将报错，直至数据与配置一致。

## 运维步骤（概要）

1. 停机或只读窗口内，将文件从本地上传目录复制到 MinIO（或反向），保持 object key 与相对路径可对应。
2. 批量更新 `t_resource_skill_ext.artifact_uri`：
   - 迁到 MinIO：把 `/uploads/skill-pack/...` 改为 `http(s)://{endpoint}/{bucket}/skill-pack/...`（object key 与 `storeSkillPack` 写入规则一致）。
   - 迁到本地：把 MinIO 完整前缀 URL 改回 `/uploads/...`，并把文件放到 `upload-dir` 下对应相对路径。
3. 修改环境变量或配置中的 `file.storage-type`，重启应用。

具体 SQL 依环境表数据编写；无通用一键脚本。
