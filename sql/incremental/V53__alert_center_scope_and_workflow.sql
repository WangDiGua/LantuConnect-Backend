ALTER TABLE `t_alert_rule`
    ADD COLUMN `scope_type` varchar(32) NOT NULL DEFAULT 'global' COMMENT '告警作用域：global/resource_type/resource' AFTER `enabled`,
    ADD COLUMN `scope_resource_type` varchar(32) DEFAULT NULL COMMENT '作用域资源类型' AFTER `scope_type`,
    ADD COLUMN `scope_resource_id` bigint DEFAULT NULL COMMENT '作用域资源ID' AFTER `scope_resource_type`,
    ADD COLUMN `label_filters_json` json DEFAULT NULL COMMENT '标签过滤条件' AFTER `scope_resource_id`;

ALTER TABLE `t_alert_record`
    ADD COLUMN `assignee_user_id` bigint DEFAULT NULL COMMENT '当前责任人' AFTER `source`,
    ADD COLUMN `ack_at` datetime DEFAULT NULL COMMENT '认领/确认时间' AFTER `assignee_user_id`,
    ADD COLUMN `silenced_at` datetime DEFAULT NULL COMMENT '静默时间' AFTER `ack_at`,
    ADD COLUMN `reopened_at` datetime DEFAULT NULL COMMENT '重开时间' AFTER `silenced_at`,
    ADD COLUMN `last_sample_value` decimal(15,4) DEFAULT NULL COMMENT '最近一次样本值' AFTER `reopened_at`,
    ADD COLUMN `trigger_snapshot_json` json DEFAULT NULL COMMENT '触发快照' AFTER `last_sample_value`,
    ADD COLUMN `rule_snapshot_json` json DEFAULT NULL COMMENT '规则快照' AFTER `trigger_snapshot_json`;

CREATE TABLE `t_alert_record_action` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `record_id` varchar(36) NOT NULL COMMENT '告警记录ID',
    `action_type` varchar(32) NOT NULL COMMENT '动作类型',
    `operator_user_id` bigint DEFAULT NULL COMMENT '操作人',
    `note` varchar(500) DEFAULT NULL COMMENT '处置备注',
    `previous_status` varchar(32) DEFAULT NULL COMMENT '变更前状态',
    `next_status` varchar(32) DEFAULT NULL COMMENT '变更后状态',
    `extra_json` json DEFAULT NULL COMMENT '扩展信息',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_alert_record_action_record_time` (`record_id`, `create_time`),
    CONSTRAINT `fk_alert_record_action_record`
        FOREIGN KEY (`record_id`) REFERENCES `t_alert_record` (`id`)
        ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警记录处置历史';

UPDATE `t_alert_rule`
SET `scope_type` = 'global'
WHERE `scope_type` IS NULL OR TRIM(`scope_type`) = '';
