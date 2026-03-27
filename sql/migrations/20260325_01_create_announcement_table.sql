-- 创建平台公告表
-- 对应前端 B7.8 公告管理功能

CREATE TABLE IF NOT EXISTS `t_announcement` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `title`       VARCHAR(200) NOT NULL COMMENT '公告标题',
    `summary`     VARCHAR(500) NOT NULL COMMENT '公告摘要',
    `content`     TEXT         NULL     COMMENT '公告正文',
    `type`        VARCHAR(50)  NOT NULL DEFAULT 'notice' COMMENT '类型：feature/maintenance/update/notice',
    `pinned`      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否置顶',
    `created_by`  BIGINT       NULL     COMMENT '创建者用户ID',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    INDEX `idx_type` (`type`),
    INDEX `idx_pinned_create` (`pinned` DESC, `create_time` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台公告';
