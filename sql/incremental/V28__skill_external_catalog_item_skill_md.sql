-- 外部技能目录镜像：同步时从 GitHub raw 预取 SKILL.md 落库，详情接口优先读库（不依赖 SkillsMP 页面、减少展示时外网请求）。

ALTER TABLE `t_skill_external_catalog_item`
    ADD COLUMN `skill_md` MEDIUMTEXT NULL COMMENT '同步时预取的 SKILL.md 全文（GitHub raw）' AFTER `stars`,
    ADD COLUMN `skill_md_resolved_url` VARCHAR(2048) NULL COMMENT '预取成功时命中的 raw 地址' AFTER `skill_md`,
    ADD COLUMN `skill_md_truncated` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '预取时是否因体积上限截断' AFTER `skill_md_resolved_url`;
