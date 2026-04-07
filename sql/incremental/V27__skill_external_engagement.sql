-- 技能在线市场外部条目：平台内收藏、评论、下载/浏览统计（与 t_skill_external_catalog_item.dedupe_key 对齐，VARCHAR(512)）

CREATE TABLE IF NOT EXISTS `t_skill_external_favorite` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`      BIGINT       NOT NULL COMMENT '收藏用户',
    `item_key`     VARCHAR(512) NOT NULL COMMENT '与镜像表 dedupe_key 一致',
    `create_time`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ext_fav_user_item` (`user_id`, `item_key`),
    KEY `idx_ext_fav_item` (`item_key`),
    CONSTRAINT `fk_ext_fav_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`user_id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='技能在线市场外部条目收藏';

CREATE TABLE IF NOT EXISTS `t_skill_external_review` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `item_key`      VARCHAR(512) NOT NULL,
    `user_id`       BIGINT       NOT NULL,
    `user_name`     VARCHAR(128) NOT NULL DEFAULT '',
    `avatar`        VARCHAR(512) NULL,
    `rating`        INT          NULL COMMENT '1～5；回复可为空',
    `comment`       TEXT         NOT NULL,
    `parent_id`     BIGINT       NULL,
    `helpful_count` INT          NOT NULL DEFAULT 0,
    `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '0 正常 1 删除',
    `create_time`   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_ext_rev_item` (`item_key`, `deleted`, `create_time`),
    KEY `idx_ext_rev_user` (`user_id`),
    CONSTRAINT `fk_ext_rev_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`user_id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `fk_ext_rev_parent` FOREIGN KEY (`parent_id`) REFERENCES `t_skill_external_review` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='技能在线市场外部条目评论';

CREATE TABLE IF NOT EXISTS `t_skill_external_download_event` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `item_key`     VARCHAR(512) NOT NULL,
    `user_id`      BIGINT       NULL COMMENT '可空：未登录埋点',
    `create_time`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_ext_dl_item` (`item_key`),
    KEY `idx_ext_dl_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='技能在线市场外部条目下载埋点';

CREATE TABLE IF NOT EXISTS `t_skill_external_view_event` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `item_key`     VARCHAR(512) NOT NULL,
    `user_id`      BIGINT       NULL,
    `create_time`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_ext_view_item` (`item_key`),
    KEY `idx_ext_view_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='技能在线市场外部条目浏览埋点';
