ALTER TABLE `t_resource_runtime_policy`
    ADD COLUMN `probe_config_json` json DEFAULT NULL COMMENT '按资源类型的探测配置' AFTER `probe_strategy`,
    ADD COLUMN `canary_payload_json` json DEFAULT NULL COMMENT '合成样例探测载荷' AFTER `probe_config_json`,
    ADD COLUMN `last_probe_evidence_json` json DEFAULT NULL COMMENT '最近一次探测结构化证据' AFTER `probe_payload_summary`;

UPDATE `t_resource_runtime_policy`
SET `probe_config_json` = CASE
        WHEN resource_type = 'agent' THEN JSON_OBJECT('latencyThresholdMs', 1500)
        WHEN resource_type = 'mcp' THEN JSON_OBJECT('requireTools', TRUE)
        WHEN resource_type = 'skill' THEN JSON_OBJECT('mode', 'canary')
        ELSE NULL
    END
WHERE `probe_config_json` IS NULL;
