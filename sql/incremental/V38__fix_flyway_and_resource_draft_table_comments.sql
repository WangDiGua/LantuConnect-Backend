-- 修复表 COMMENT：flyway_schema_history 无注释；t_resource_draft 历史乱码。MySQL 8 utf8mb4。
--
-- Windows 执行勿用 PowerShell「中文 here-string 管道」写入，易损坏 UTF-8；请用：
--   chcp 65001
--   mysql ... --default-character-set=utf8mb4 lantu_connect < 本文件

SET NAMES utf8mb4;

ALTER TABLE `flyway_schema_history` COMMENT = 'Flyway 数据库版本迁移历史表';

ALTER TABLE `t_resource_draft` COMMENT = '已发布资源编辑草稿（JSON）';
