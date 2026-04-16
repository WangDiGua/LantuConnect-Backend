ALTER TABLE `t_alert_rule`
    ADD COLUMN `scope_type` varchar(32) NOT NULL DEFAULT 'global' COMMENT 'alert scope: global/resource_type/resource' AFTER `enabled`,
    ADD COLUMN `scope_resource_type` varchar(32) DEFAULT NULL COMMENT 'scoped resource type' AFTER `scope_type`,
    ADD COLUMN `scope_resource_id` bigint DEFAULT NULL COMMENT 'scoped resource id' AFTER `scope_resource_type`,
    ADD COLUMN `label_filters` json DEFAULT NULL COMMENT 'label filter conditions' AFTER `scope_resource_id`;

ALTER TABLE `t_alert_record`
    ADD COLUMN `assignee_user_id` bigint DEFAULT NULL COMMENT 'current assignee' AFTER `source`,
    ADD COLUMN `ack_at` datetime DEFAULT NULL COMMENT 'acknowledged at' AFTER `assignee_user_id`,
    ADD COLUMN `silenced_at` datetime DEFAULT NULL COMMENT 'silenced at' AFTER `ack_at`,
    ADD COLUMN `reopened_at` datetime DEFAULT NULL COMMENT 'reopened at' AFTER `silenced_at`,
    ADD COLUMN `last_sample_value` decimal(15,4) DEFAULT NULL COMMENT 'last sample value' AFTER `reopened_at`,
    ADD COLUMN `trigger_snapshot_json` json DEFAULT NULL COMMENT 'trigger snapshot' AFTER `last_sample_value`,
    ADD COLUMN `rule_snapshot_json` json DEFAULT NULL COMMENT 'rule snapshot' AFTER `trigger_snapshot_json`;

CREATE TABLE `t_alert_record_action` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `record_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'alert record id',
    `action_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'action type',
    `operator_user_id` bigint DEFAULT NULL COMMENT 'operator user id',
    `note` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'action note',
    `previous_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'status before change',
    `next_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'status after change',
    `extra_json` json DEFAULT NULL COMMENT 'extra action payload',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
    PRIMARY KEY (`id`),
    KEY `idx_alert_record_action_record_time` (`record_id`, `create_time`),
    CONSTRAINT `fk_alert_record_action_record`
        FOREIGN KEY (`record_id`) REFERENCES `t_alert_record` (`id`)
        ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='alert record action history';

UPDATE `t_alert_rule`
SET `scope_type` = 'global'
WHERE `scope_type` IS NULL OR TRIM(`scope_type`) = '';
