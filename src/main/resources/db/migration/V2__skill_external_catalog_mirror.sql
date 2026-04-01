-- 技能在线市场：远程目录镜像（SkillsMP ∪ 镜像 JSON），按 packUrl / id 去重键 upsert；坏网时读库内快照。

CREATE TABLE IF NOT EXISTS `t_skill_external_catalog_item` (
    `dedupe_key`    VARCHAR(512)  NOT NULL COMMENT '去重主键：有 pack_url 时为 trim+小写，否则为 id:+external_id',
    `external_id`   VARCHAR(256)  NOT NULL DEFAULT '' COMMENT '外部目录条目 id（如 SkillsMP id、gh-owner-repo 等）',
    `name`          VARCHAR(512)  NOT NULL DEFAULT '' COMMENT '展示名称',
    `summary`       TEXT          NULL COMMENT '简介/描述',
    `pack_url`      VARCHAR(2048) NOT NULL DEFAULT '' COMMENT '技能包 zip 等直链（展示用，可以与 dedupe_key 同源）',
    `license_note`  VARCHAR(768)  NULL COMMENT '许可与数据来源说明',
    `source_url`    VARCHAR(2048) NULL COMMENT '来源页或仓库链接',
    `stars`         INT           NULL COMMENT '星标等热度指标（可选）',
    `sync_batch`    BIGINT        NOT NULL DEFAULT 0 COMMENT '最近一次全量同步的批次号；小于当前批次的行会被删除',
    `create_time`   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time`   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    PRIMARY KEY (`dedupe_key`),
    KEY `idx_skill_ext_cat_batch` (`sync_batch`),
    KEY `idx_skill_ext_cat_stars` (`stars`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='技能在线市场远程目录镜像条目（与列表 API 字段一致）';

CREATE TABLE IF NOT EXISTS `t_skill_external_catalog_sync` (
    `id`               TINYINT     NOT NULL DEFAULT 1 COMMENT '固定 1，单行元数据',
    `last_success_at`  DATETIME(3) NULL COMMENT '最后一次成功同步完成时间',
    `last_attempt_at`  DATETIME(3) NULL COMMENT '最后一次发起同步的时间',
    `last_error`       VARCHAR(1024) NULL COMMENT '最近一次同步失败原因摘要',
    `current_batch`    BIGINT      NOT NULL DEFAULT 0 COMMENT '当前同步批次号，与 t_skill_external_catalog_item.sync_batch 对齐',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='技能在线市场目录同步状态（单行）';

INSERT IGNORE INTO `t_skill_external_catalog_sync` (`id`, `current_batch`) VALUES (1, 0);
