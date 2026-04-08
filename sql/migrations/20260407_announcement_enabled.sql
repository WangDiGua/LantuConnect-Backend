-- 公告：对用户端（探索页等）是否展示
ALTER TABLE `t_announcement`
  ADD COLUMN `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否对用户端展示' AFTER `pinned`;
