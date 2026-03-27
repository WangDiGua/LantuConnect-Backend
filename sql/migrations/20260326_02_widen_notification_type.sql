-- 通知 type 原 varchar(16) 无法容纳 resource_submitted / resource_published / grant_application_new 等事件码，会导致插入失败与接口 500
ALTER TABLE t_notification
    MODIFY COLUMN `type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'notice / alert / system / 业务事件码';
