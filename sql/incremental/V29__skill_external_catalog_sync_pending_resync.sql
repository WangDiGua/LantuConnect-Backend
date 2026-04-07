-- 技能在线市场库同步：配置变更或抢锁失败后需再跑全量同步
ALTER TABLE `t_skill_external_catalog_sync`
    ADD COLUMN `pending_resync` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '1=需在成功同步后再补跑一轮' AFTER `last_error`;
