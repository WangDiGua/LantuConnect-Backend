-- 评论楼中楼：回复指向父评论；与前端 POST /reviews body.parentId 对齐
ALTER TABLE `t_review`
  ADD COLUMN `parent_id` bigint NULL DEFAULT NULL COMMENT '父评论 id，顶级为 NULL' AFTER `target_id`,
  ADD INDEX `idx_review_parent` (`parent_id` ASC),
  ADD CONSTRAINT `fk_review_parent` FOREIGN KEY (`parent_id`) REFERENCES `t_review` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT;
