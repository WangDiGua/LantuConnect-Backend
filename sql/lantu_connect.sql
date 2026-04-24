/*
 Navicat Premium Data Transfer

 Source Server         : 鐜嬪笣鏈湴鐨凪YSQL鏁版嵁搴?
 Source Server Type    : MySQL
 Source Server Version : 80044 (8.0.44)
 Source Host           : localhost:3306
 Source Schema         : lantu_connect

 Target Server Type    : MySQL
 Target Server Version : 80044 (8.0.44)
 File Encoding         : 65001

 Date: 25/03/2026 17:48:57
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for t_alert_record
-- ----------------------------
DROP TABLE IF EXISTS `t_alert_record`;
CREATE TABLE `t_alert_record`  (
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `rule_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `rule_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `severity` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `source` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `labels` json NULL,
  `fired_at` datetime NOT NULL,
  `resolved_at` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_alert_record_rule`(`rule_id` ASC) USING BTREE,
  CONSTRAINT `fk_alert_record_rule` FOREIGN KEY (`rule_id`) REFERENCES `t_alert_rule` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '鍛婅璁板綍琛? ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of t_alert_record
-- ----------------------------
INSERT INTO `t_alert_record` VALUES ('arec-001', 'ar-001', 'API寤惰繜杩囬珮', 'warning', 'resolved', 'P95寤惰繜杈惧埌3200ms锛岃秴杩囬槇鍊?000ms', 'monitor-service', '{\"agent\": \"web-search\"}', '2026-03-21 07:50:00', '2026-03-21 08:10:00');
INSERT INTO `t_alert_record` VALUES ('arec-002', 'ar-002', '閿欒鐜囪繃楂?, 'critical', 'firing', '鏈€杩?鍒嗛挓閿欒鐜?.2%锛岃秴杩囬槇鍊?%', 'monitor-service', '{\"agent\": \"campus-qa\"}', '2026-03-21 14:00:00', NULL);

-- ----------------------------
-- Table structure for t_alert_record_action
-- ----------------------------
DROP TABLE IF EXISTS `t_alert_record_action`;
CREATE TABLE `t_alert_record_action`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `record_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'alert record id',
  `action_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'action type',
  `operator_user_id` bigint NULL DEFAULT NULL COMMENT 'operator user id',
  `note` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'action note',
  `previous_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'status before change',
  `next_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'status after change',
  `extra_json` json NULL COMMENT 'extra action payload',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_alert_record_action_record_time`(`record_id` ASC, `create_time` ASC) USING BTREE,
  CONSTRAINT `fk_alert_record_action_record` FOREIGN KEY (`record_id`) REFERENCES `t_alert_record` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'alert record action history' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for t_alert_rule
-- ----------------------------
DROP TABLE IF EXISTS `t_alert_rule`;
CREATE TABLE `t_alert_rule`  (
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL,
  `metric` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `operator` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `threshold` decimal(15, 4) NOT NULL,
  `duration` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '5m',
  `severity` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `enabled` tinyint(1) NULL DEFAULT 1,
  `notify_channels` json NULL,
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '鍛婅瑙勫垯琛? ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of t_alert_rule
-- ----------------------------
INSERT INTO `t_alert_rule` VALUES ('ar-001', 'API寤惰繜杩囬珮', 'P95寤惰繜瓒呰繃3绉掓椂鍛婅', 'api.latency.p95', 'gt', 3000.0000, '5m', 'warning', 1, '[]', '2026-03-22 10:58:54', '2026-03-22 10:58:54');
INSERT INTO `t_alert_rule` VALUES ('ar-002', '閿欒鐜囪繃楂?, '5鍒嗛挓鍐呴敊璇巼瓒呰繃5%鏃跺憡璀?, 'api.error_rate', 'gt', 5.0000, '5m', 'critical', 1, '[]', '2026-03-22 10:58:54', '2026-03-22 10:58:54');

-- ----------------------------
-- Table structure for t_announcement
-- ----------------------------
DROP TABLE IF EXISTS `t_announcement`;
CREATE TABLE `t_announcement`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '鍏憡鏍囬',
  `summary` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '鍏憡鎽樿',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '鍏憡姝ｆ枃',
  `type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'notice' COMMENT '绫诲瀷锛歠eature/maintenance/update/notice',
  `pinned` tinyint(1) NOT NULL DEFAULT 0 COMMENT '鏄惁缃《',
  `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '鏄惁瀵圭敤鎴风灞曠ず',
  `created_by` bigint NULL DEFAULT NULL COMMENT '鍒涘缓鑰呯敤鎴稩D',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '閫昏緫鍒犻櫎',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_type`(`type` ASC) USING BTREE,
  INDEX `idx_pinned_create`(`pinned` DESC, `create_time` DESC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '骞冲彴鍏憡' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of t_announcement
-- ----------------------------

-- ----------------------------
-- Table structure for t_api_key
-- ----------------------------
DROP TABLE IF EXISTS `t_api_key`;
CREATE TABLE `t_api_key`  (
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `key_hash` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `secret_ciphertext` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL,
  `prefix` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `masked_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `owner_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `owner_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `scopes` json NULL,
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'active',
  `expires_at` datetime NULL DEFAULT NULL,
  `last_used_at` datetime NULL DEFAULT NULL,
  `call_count` bigint NULL DEFAULT 0,
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_api_key_owner`(`owner_type` ASC, `owner_id` ASC) USING BTREE,
  INDEX `idx_api_key_status`(`status` ASC) USING BTREE,
  INDEX `idx_api_key_prefix`(`prefix` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'API 瀵嗛挜琛? ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of t_api_key
-- ----------------------------

-- ----------------------------
-- Table structure for t_audit_item
-- ----------------------------
DROP TABLE IF EXISTS `t_audit_item`;
CREATE TABLE `t_audit_item`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `target_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `target_id` bigint NOT NULL,
  `display_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `agent_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL,
  `agent_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `source_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `submitter` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `submit_time` datetime NOT NULL,
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'pending_review',
  `reviewer_id` bigint NULL DEFAULT NULL,
  `reject_reason` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL,
  `review_time` datetime NULL DEFAULT NULL,
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '瀹℃牳闃熷垪琛? ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of t_audit_item
-- ----------------------------
INSERT INTO `t_audit_item` VALUES (1, 'agent', 5, '鍥惧儚鐢熸垚', 'image-gen', '鏍规嵁鏂囧瓧鎻忚堪鐢熸垚楂樿川閲忓浘鍍?, 'http_api', 'cloud', '鐜嬪紑鍙?, '2026-03-20 09:00:00', 'pending_review', NULL, NULL, NULL, '2026-03-22 10:58:54');
INSERT INTO `t_audit_item` VALUES (2, 'mcp', 15, 'OCR 鏂囧瓧璇嗗埆', 'ocr-recognize', '璇嗗埆鍥剧墖涓殑鏂囧瓧鍐呭', 'http_api', 'cloud', '鐜嬪紑鍙?, '2026-03-20 14:30:00', 'published', NULL, NULL, '2026-03-25 09:59:40', '2026-03-22 10:58:54');
INSERT INTO `t_audit_item` VALUES (3, 'mcp', 29, 'Smoke MCP', 'smoke-mcp-1774368649', 'smoke test', 'mcp', 'internal', '1', '2026-03-25 00:10:49', 'published', NULL, NULL, '2026-03-25 00:10:50', '2026-03-25 00:10:49');
INSERT INTO `t_audit_item` VALUES (4, 'mcp', 30, 'Smoke2 MCP', 'smoke2-mcp-1774368774', 'smoke test 2', 'mcp', 'internal', '1', '2026-03-25 00:12:54', 'published', NULL, NULL, '2026-03-25 00:12:55', '2026-03-25 00:12:54');

-- ----------------------------
-- Table structure for t_audit_log
-- ----------------------------
DROP TABLE IF EXISTS `t_audit_log`;
CREATE TABLE `t_audit_log`  (
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `user_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `username` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `action` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `resource` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `resource_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `details` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL,
  `ip` varchar(45) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `user_agent` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `result` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_audit_log_user_time`(`user_id` ASC, `create_time` ASC) USING BTREE,
  INDEX `idx_audit_log_action`(`action` ASC) USING BTREE,
  INDEX `idx_audit_log_resource`(`resource` ASC, `resource_id` ASC) USING BTREE,
  INDEX `idx_audit_log_time`(`create_time` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '瀹¤鏃ュ織琛? ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of t_audit_log
-- ----------------------------
INSERT INTO `t_audit_log` VALUES ('0b15ef9e-696e-4da9-96cd-be8839875375', '1', 'user-1', 'resource_version_switch', 'resource-center', NULL, 'elapsedMs=8', '0:0:0:0:0:0:0:1', NULL, 'success', '2026-03-25 00:12:55');
INSERT INTO `t_audit_log` VALUES ('1f39d996-5ced-4ec9-94da-4264a2143e76', '1', 'user-1', 'resource_version_create', 'resource-center', NULL, 'elapsedMs=11', '0:0:0:0:0:0:0:1', NULL, 'success', '2026-03-25 00:12:55');
INSERT INTO `t_audit_log` VALUES ('2746f274-b91f-4e4a-abf7-9883a9917249', '1', 'user-1', 'resource_version_create', 'resource-center', NULL, 'elapsedMs=12', '0:0:0:0:0:0:0:1', NULL, 'success', '2026-03-25 00:10:50');
INSERT INTO `t_audit_log` VALUES ('2cdf43b5-d9be-4ff0-8484-24c875baff04', '3', 'user-3', 'resource_version_switch', 'resource-center', NULL, 'elapsedMs=14', '0:0:0:0:0:0:0:1', NULL, 'success', '2026-03-25 14:53:23');
INSERT INTO `t_audit_log` VALUES ('2dba00f4-4f19-430d-964e-89b030ffc4ac', '3', 'user-3', 'resource_deprecate', 'resource-center', NULL, 'elapsedMs=10', '0:0:0:0:0:0:0:1', NULL, 'success', '2026-03-25 09:28:56');
INSERT INTO `t_audit_log` VALUES ('3266b057-d193-4be1-9ae4-f6693b3aca29', '1', 'user-1', 'resource_create', 'resource-center', NULL, 'elapsedMs=34', '0:0:0:0:0:0:0:1', NULL, 'success', '2026-03-25 00:10:50');
INSERT INTO `t_audit_log` VALUES ('465d4418-ba3f-4105-b0a8-369878185ef8', '3', 'user-3', 'resource_version_switch', 'resource-center', NULL, 'elapsedMs=6', '0:0:0:0:0:0:0:1', NULL, 'success', '2026-03-25 14:53:24');
INSERT INTO `t_audit_log` VALUES ('5b47ed37-c525-4a13-99bf-26d5b348665d', '3', 'user-3', 'resource_version_switch', 'resource-center', NULL, 'elapsedMs=9', '0:0:0:0:0:0:0:1', NULL, 'success', '2026-03-25 14:53:26');
INSERT INTO `t_audit_log` VALUES ('7731f037-da0d-4027-a073-848dd0324ec5', '1', 'user-1', 'resource_create', 'resource-center', NULL, 'elapsedMs=27', '0:0:0:0:0:0:0:1', NULL, 'success', '2026-03-25 00:12:55');
INSERT INTO `t_audit_log` VALUES ('9594dce0-3963-4c65-90ed-4ad4d8c29a3f', '1', 'user-1', 'resource_submit', 'resource-center', NULL, 'elapsedMs=10', '0:0:0:0:0:0:0:1', NULL, 'success', '2026-03-25 00:10:50');
INSERT INTO `t_audit_log` VALUES ('a5f50c5b-0a52-4095-bd33-aec7110ce0c2', '3', 'user-3', 'resource_deprecate', 'resource-center', NULL, 'elapsedMs=12', '0:0:0:0:0:0:0:1', NULL, 'success', '2026-03-25 09:28:39');
INSERT INTO `t_audit_log` VALUES ('al-001', '1', 'admin', 'login', 'auth', NULL, '绠＄悊鍛樼櫥褰?, '10.0.0.1', NULL, 'success', '2026-03-22 10:58:54');
INSERT INTO `t_audit_log` VALUES ('al-002', '1', 'admin', 'create_agent', 'agent', '1', '鍒涘缓Agent锛氳仈缃戞悳绱?, '10.0.0.1', NULL, 'success', '2026-03-22 10:58:54');
INSERT INTO `t_audit_log` VALUES ('al-003', '1', 'admin', 'create_agent', 'agent', '2', '鍒涘缓Agent锛氭櫤鑳藉璇惧姪鎵?, '10.0.0.1', NULL, 'success', '2026-03-22 10:58:54');
INSERT INTO `t_audit_log` VALUES ('al-004', '3', 'developer', 'create_agent', 'agent', '5', '鍒涘缓Agent锛氬浘鍍忕敓鎴?, '10.0.0.2', NULL, 'success', '2026-03-22 10:58:54');
INSERT INTO `t_audit_log` VALUES ('al-005', '1', 'admin', 'approve', 'audit', '1', '瀹℃牳閫氳繃锛氳仈缃戞悳绱?, '10.0.0.1', NULL, 'success', '2026-03-22 10:58:54');
INSERT INTO `t_audit_log` VALUES ('al-006', '1', 'admin', 'update_system_param', 'system-config', 'session_timeout_minutes', '鏇存柊绯荤粺鍙傛暟锛氫細璇濊秴鏃讹紙鍒嗛挓锛?, '10.0.0.1', NULL, 'success', '2026-03-22 10:58:54');
INSERT INTO `t_audit_log` VALUES ('b5767acb-d0bd-4c14-bea9-21a8c987505a', '1', 'user-1', 'resource_submit', 'resource-center', NULL, 'elapsedMs=10', '0:0:0:0:0:0:0:1', NULL, 'success', '2026-03-25 00:12:55');
INSERT INTO `t_audit_log` VALUES ('be275927-affa-4f65-8818-558ce3e3d172', '1', 'user-1', 'resource_version_switch', 'resource-center', NULL, 'elapsedMs=8', '0:0:0:0:0:0:0:1', NULL, 'success', '2026-03-25 00:10:50');

-- ----------------------------
-- Table structure for t_call_log
-- ----------------------------
DROP TABLE IF EXISTS `t_call_log`;
CREATE TABLE `t_call_log`  (
  `usage_id` bigint NOT NULL AUTO_INCREMENT COMMENT '浣跨敤璁板綍搴忓彿',
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `trace_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `agent_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `agent_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `display_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '显示名称',
  `resource_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '缃戝叧璋冪敤鐩爣璧勬簮绫诲瀷',
  `user_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `method` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `action` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'invoke' COMMENT '操作',
  `input_preview` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '输入摘要',
  `output_preview` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '输出摘要',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `status_code` smallint NOT NULL,
  `latency_ms` int NOT NULL,
  `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL,
  `ip` varchar(45) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `usage_id`(`usage_id` ASC) USING BTREE,
  INDEX `idx_call_log_trace`(`trace_id` ASC) USING BTREE,
  INDEX `idx_call_log_agent_time`(`agent_id` ASC, `create_time` ASC) USING BTREE,
  INDEX `idx_call_log_resource_agent_time`(`resource_type` ASC, `agent_id` ASC, `create_time` ASC) USING BTREE,
  INDEX `idx_call_log_user_time`(`user_id` ASC, `create_time` ASC) USING BTREE,
  INDEX `idx_call_log_status`(`status` ASC) USING BTREE,
  INDEX `idx_call_log_time`(`create_time` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '璋冪敤鏃ュ織琛? ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of t_call_log
-- ----------------------------
INSERT INTO `t_call_log` (`id`, `trace_id`, `agent_id`, `agent_name`, `resource_type`, `user_id`, `method`, `status`, `status_code`, `latency_ms`, `error_message`, `ip`, `create_time`) VALUES ('cl-001', 'trace-a001', '1', 'web-search', NULL, '4', 'POST /chat', 'success', 200, 1150, NULL, '10.0.0.1', '2026-03-22 10:58:54');
INSERT INTO `t_call_log` (`id`, `trace_id`, `agent_id`, `agent_name`, `resource_type`, `user_id`, `method`, `status`, `status_code`, `latency_ms`, `error_message`, `ip`, `create_time`) VALUES ('cl-002', 'trace-a002', '1', 'web-search', NULL, '4', 'POST /chat', 'success', 200, 980, NULL, '10.0.0.1', '2026-03-22 10:58:54');
INSERT INTO `t_call_log` (`id`, `trace_id`, `agent_id`, `agent_name`, `resource_type`, `user_id`, `method`, `status`, `status_code`, `latency_ms`, `error_message`, `ip`, `create_time`) VALUES ('cl-003', 'trace-a003', '2', 'smart-tutor', NULL, '3', 'POST /chat', 'success', 200, 2800, NULL, '10.0.0.2', '2026-03-22 10:58:54');
INSERT INTO `t_call_log` (`id`, `trace_id`, `agent_id`, `agent_name`, `resource_type`, `user_id`, `method`, `status`, `status_code`, `latency_ms`, `error_message`, `ip`, `create_time`) VALUES ('cl-004', 'trace-a004', '3', 'paper-polish', NULL, '3', 'POST /chat', 'success', 200, 3500, NULL, '10.0.0.2', '2026-03-22 10:58:54');
INSERT INTO `t_call_log` (`id`, `trace_id`, `agent_id`, `agent_name`, `resource_type`, `user_id`, `method`, `status`, `status_code`, `latency_ms`, `error_message`, `ip`, `create_time`) VALUES ('cl-005', 'trace-a005', '4', 'campus-qa', NULL, '4', 'POST /chat', 'success', 200, 650, NULL, '10.0.0.3', '2026-03-22 10:58:54');
INSERT INTO `t_call_log` (`id`, `trace_id`, `agent_id`, `agent_name`, `resource_type`, `user_id`, `method`, `status`, `status_code`, `latency_ms`, `error_message`, `ip`, `create_time`) VALUES ('cl-006', 'trace-a006', '6', 'code-assistant', NULL, '3', 'POST /chat', 'success', 200, 1900, NULL, '10.0.0.2', '2026-03-22 10:58:54');
INSERT INTO `t_call_log` (`id`, `trace_id`, `agent_id`, `agent_name`, `resource_type`, `user_id`, `method`, `status`, `status_code`, `latency_ms`, `error_message`, `ip`, `create_time`) VALUES ('cl-007', 'trace-a007', '1', 'web-search', NULL, '2', 'POST /chat', 'error', 500, 5200, NULL, '10.0.0.4', '2026-03-22 10:58:54');
INSERT INTO `t_call_log` (`id`, `trace_id`, `agent_id`, `agent_name`, `resource_type`, `user_id`, `method`, `status`, `status_code`, `latency_ms`, `error_message`, `ip`, `create_time`) VALUES ('cl-008', 'trace-a008', '6', 'code-assistant', NULL, '4', 'POST /chat', 'success', 200, 1600, NULL, '10.0.0.3', '2026-03-22 10:58:54');
INSERT INTO `t_call_log` (`id`, `trace_id`, `agent_id`, `agent_name`, `resource_type`, `user_id`, `method`, `status`, `status_code`, `latency_ms`, `error_message`, `ip`, `create_time`) VALUES ('cl-009', 'trace-a009', '2', 'smart-tutor', NULL, '2', 'POST /chat', 'success', 200, 2200, NULL, '10.0.0.4', '2026-03-22 10:58:54');
INSERT INTO `t_call_log` (`id`, `trace_id`, `agent_id`, `agent_name`, `resource_type`, `user_id`, `method`, `status`, `status_code`, `latency_ms`, `error_message`, `ip`, `create_time`) VALUES ('cl-010', 'trace-a010', '4', 'campus-qa', NULL, '4', 'POST /chat', 'timeout', 504, 15000, NULL, '10.0.0.3', '2026-03-22 10:58:54');

-- ----------------------------
-- Table structure for t_developer_application
-- ----------------------------
DROP TABLE IF EXISTS `t_developer_application`;
CREATE TABLE `t_developer_application`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `contact_email` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `contact_phone` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `company_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `apply_reason` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'pending',
  `review_comment` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `reviewed_by` bigint NULL DEFAULT NULL,
  `reviewed_at` datetime NULL DEFAULT NULL,
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_dev_apply_user`(`user_id` ASC) USING BTREE,
  INDEX `idx_dev_apply_status`(`status` ASC) USING BTREE,
  CONSTRAINT `fk_dev_apply_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`user_id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '寮€鍙戣€呭叆椹荤敵璇疯〃' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of t_developer_application
-- ----------------------------

-- ----------------------------
-- Table structure for t_favorite
-- ----------------------------
DROP TABLE IF EXISTS `t_favorite`;
CREATE TABLE `t_favorite`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `target_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `target_id` bigint NOT NULL,
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_favorite`(`user_id` ASC, `target_type` ASC, `target_id` ASC) USING BTREE,
  CONSTRAINT `fk_favorite_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`user_id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 10 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '鏀惰棌琛? ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of t_favorite
-- ----------------------------
INSERT INTO `t_favorite` VALUES (1, 4, 'agent', 1, '2026-03-22 10:58:54');
INSERT INTO `t_favorite` VALUES (2, 4, 'agent', 6, '2026-03-22 10:58:54');
INSERT INTO `t_favorite` VALUES (3, 4, 'mcp', 9, '2026-03-22 10:58:54');
INSERT INTO `t_favorite` VALUES (7, 6, 'agent', 2, '2026-03-23 23:11:31');
INSERT INTO `t_favorite` VALUES (8, 3, 'agent', 6, '2026-03-24 23:19:24');
INSERT INTO `t_favorite` VALUES (9, 2, 'agent', 3, '2026-03-24 23:22:32');

-- ----------------------------
-- Table structure for t_login_history
-- ----------------------------
DROP TABLE IF EXISTS `t_login_history`;
CREATE TABLE `t_login_history`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `username` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `login_time` datetime NOT NULL,
  `ip` varchar(45) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `user_agent` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `login_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'password / cas / sms',
  `result` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'success / failure',
  `failure_reason` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `location` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'IP褰掑睘鍦?,
  `device` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '璁惧淇℃伅',
  `os` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '鎿嶄綔绯荤粺',
  `browser` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '娴忚鍣?,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_login_history_user`(`user_id` ASC, `login_time` ASC) USING BTREE,
  INDEX `idx_login_history_time`(`login_time` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 76 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '鐧诲綍鍘嗗彶琛? ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of t_login_history
-- ----------------------------
INSERT INTO `t_login_history` VALUES (1, 1, 'admin', '2026-03-22 13:13:35', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (2, 1, 'admin', '2026-03-22 21:14:30', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (3, 1, 'admin', '2026-03-22 21:15:29', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (4, 4, 'testuser', '2026-03-22 21:33:41', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (5, 3, 'developer', '2026-03-22 21:34:30', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (6, 2, 'dept_admin', '2026-03-22 21:35:12', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (7, 1, 'admin', '2026-03-22 21:57:59', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (8, 1, 'admin', '2026-03-22 22:10:48', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (9, 1, 'admin', '2026-03-22 22:10:59', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (10, 1, 'admin', '2026-03-22 22:15:45', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (11, 1, 'admin', '2026-03-22 22:21:05', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (12, 1, 'admin', '2026-03-22 23:05:48', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT; Windows NT 10.0; zh-CN) WindowsPowerShell/5.1.22621.2506', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (13, 1, 'admin', '2026-03-23 00:37:56', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (14, 1, 'admin', '2026-03-23 00:41:06', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (15, 6, 'test002', '2026-03-23 00:57:43', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (16, 3, 'developer', '2026-03-23 00:58:03', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (17, 1, 'admin', '2026-03-23 00:59:31', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (18, 1, 'admin', '2026-03-23 09:18:12', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (19, 1, 'admin', '2026-03-23 09:36:59', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (20, 4, 'testuser', '2026-03-23 09:40:27', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (21, 4, 'testuser', '2026-03-23 09:42:36', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (22, 1, 'admin', '2026-03-23 10:25:13', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (23, 3, 'developer', '2026-03-23 10:54:28', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (24, 1, 'admin', '2026-03-23 11:39:06', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (25, 1, 'admin', '2026-03-23 14:01:23', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (26, 1, 'admin', '2026-03-23 14:57:17', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (27, 4, 'testuser', '2026-03-23 15:38:48', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (28, 1, 'admin', '2026-03-23 15:40:03', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (29, 1, 'admin', '2026-03-23 22:17:09', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (30, 1, 'admin', '2026-03-23 22:57:22', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (31, 1, 'admin', '2026-03-23 23:07:39', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (32, 6, 'test002', '2026-03-23 23:09:35', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (33, 6, 'test002', '2026-03-24 10:00:15', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (34, 1, 'admin', '2026-03-24 11:28:56', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (35, 1, 'admin', '2026-03-24 20:28:54', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (36, 1, 'admin', '2026-03-24 22:41:07', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (37, 6, 'test002', '2026-03-24 23:04:21', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (38, 3, 'developer', '2026-03-24 23:06:22', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (39, 6, 'test002', '2026-03-24 23:21:11', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (40, 6, 'test002', '2026-03-24 23:21:20', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (41, 2, 'dept_admin', '2026-03-24 23:21:38', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (42, 2, 'dept_admin', '2026-03-24 23:21:53', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (43, 3, 'developer', '2026-03-25 09:25:53', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (44, 6, 'test002', '2026-03-25 09:29:37', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (45, 2, 'dept_admin', '2026-03-25 09:30:06', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (46, 2, 'dept_admin', '2026-03-25 09:30:23', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (47, 1, 'admin', '2026-03-25 09:30:41', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (48, 1, 'admin', '2026-03-25 09:31:05', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (49, 1, 'admin', '2026-03-25 09:34:14', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (50, 1, 'admin', '2026-03-25 09:37:51', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (51, 3, 'developer', '2026-03-25 10:00:31', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (52, 3, 'developer', '2026-03-25 10:41:24', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (53, 3, 'developer', '2026-03-25 15:04:13', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (54, 3, 'developer', '2026-03-25 15:07:34', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (55, 1, 'admin', '2026-03-25 15:07:44', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (56, 1, 'admin', '2026-03-25 15:23:52', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (57, 1, 'admin', '2026-03-25 15:24:16', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (58, 1, 'admin', '2026-03-25 15:41:44', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (59, 1, 'admin', '2026-03-25 15:46:52', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (60, 1, 'admin', '2026-03-25 15:52:53', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (61, 1, 'admin', '2026-03-25 15:53:45', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (62, 1, 'admin', '2026-03-25 15:59:44', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (63, 6, 'test002', '2026-03-25 16:09:48', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (64, 6, 'test002', '2026-03-25 16:10:05', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (65, 1, 'admin', '2026-03-25 16:13:48', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (66, 6, 'test002', '2026-03-25 16:16:54', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (67, 1, 'admin', '2026-03-25 16:26:42', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (68, 1, 'admin', '2026-03-25 16:29:39', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (69, 1, 'admin', '2026-03-25 16:30:46', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (70, 3, 'developer', '2026-03-25 16:31:02', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (71, 3, 'developer', '2026-03-25 16:43:20', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (72, 2, 'dept_admin', '2026-03-25 16:44:12', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (73, 2, 'dept_admin', '2026-03-25 16:48:30', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (74, 3, 'developer', '2026-03-25 17:05:19', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);
INSERT INTO `t_login_history` VALUES (75, 3, 'developer', '2026-03-25 17:47:15', '0:0:0:0:0:0:0:1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36', 'password', 'success', NULL, NULL, NULL, NULL, NULL);

-- ----------------------------
-- Table structure for t_notification
-- ----------------------------
DROP TABLE IF EXISTS `t_notification`;
CREATE TABLE `t_notification`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '鎺ユ敹鐢ㄦ埛ID, 0=骞挎挱',
  `type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'notice / alert / system / 涓氬姟浜嬩欢鐮?,
  `category` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'notice' COMMENT 'workflow / notice / alert / system / security',
  `severity` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'info' COMMENT 'info / success / warning / error',
  `title` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `body` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL,
  `source_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '鏉ユ簮绫诲瀷: audit / alert / system_announce',
  `source_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '鏉ユ簮璧勬簮ID',
  `aggregate_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '鍚屼竴鐢ㄦ埛鍐呰仛鍚堟祦绋嬩富鍗＄墖鐨勯敭',
  `flow_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'running / success / failed / warning',
  `current_step` int NULL DEFAULT NULL,
  `total_steps` int NULL DEFAULT NULL,
  `steps_json` json NULL,
  `action_label` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `action_url` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `metadata_json` json NULL,
  `is_read` tinyint(1) NULL DEFAULT 0,
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `last_event_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_notification_user`(`user_id` ASC, `is_read` ASC) USING BTREE,
  INDEX `idx_notification_time`(`create_time` ASC) USING BTREE,
  INDEX `idx_notification_aggregate`(`user_id` ASC, `aggregate_key` ASC) USING BTREE,
  INDEX `idx_notification_category`(`user_id` ASC, `category` ASC, `is_read` ASC) USING BTREE,
  INDEX `idx_notification_last_event`(`user_id` ASC, `last_event_time` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '閫氱煡琛? ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of t_notification
-- ----------------------------
INSERT INTO `t_notification` (`id`, `user_id`, `type`, `category`, `severity`, `title`, `body`, `source_type`, `source_id`, `is_read`, `create_time`, `update_time`, `last_event_time`) VALUES (1, 0, 'system', 'system', 'info', '绯荤粺缁存姢鍏憡', '鍏版櫤閫氬钩鍙板皢浜?026骞?鏈?5鏃ュ噷鏅?:00-4:00杩涜绯荤粺缁存姢鍗囩骇锛屽眾鏃舵湇鍔″皢鏆傚仠銆?, 'system_announce', NULL, 0, '2026-03-22 10:58:54', '2026-03-22 10:58:54', '2026-03-22 10:58:54');
INSERT INTO `t_notification` (`id`, `user_id`, `type`, `category`, `severity`, `title`, `body`, `source_type`, `source_id`, `is_read`, `create_time`, `update_time`, `last_event_time`) VALUES (2, 3, 'notice', 'notice', 'info', '鎮ㄧ殑 Agent銆屽浘鍍忕敓鎴愩€嶅凡鎻愪氦瀹℃牳', '鎮ㄦ彁浜ょ殑 Agent銆屽浘鍍忕敓鎴愩€嶆鍦ㄧ瓑寰呯鐞嗗憳瀹℃牳锛岄璁?-2涓伐浣滄棩鍐呭畬鎴愩€?, 'audit', '5', 0, '2026-03-22 10:58:54', '2026-03-22 10:58:54', '2026-03-22 10:58:54');
INSERT INTO `t_notification` (`id`, `user_id`, `type`, `category`, `severity`, `title`, `body`, `source_type`, `source_id`, `is_read`, `create_time`, `update_time`, `last_event_time`) VALUES (3, 3, 'notice', 'notice', 'info', '鎮ㄧ殑 Skill銆孫CR 鏂囧瓧璇嗗埆銆嶅凡鎻愪氦瀹℃牳', '鎮ㄦ彁浜ょ殑 Skill銆孫CR 鏂囧瓧璇嗗埆銆嶆鍦ㄧ瓑寰呭鏍搞€?, 'audit', '8', 1, '2026-03-22 10:58:54', '2026-03-22 10:58:54', '2026-03-22 10:58:54');

-- ----------------------------
-- Table structure for t_org_menu
-- ----------------------------
DROP TABLE IF EXISTS `t_org_menu`;
CREATE TABLE `t_org_menu`  (
  `menu_id` bigint NOT NULL AUTO_INCREMENT,
  `menu_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `menu_parent_id` bigint NULL DEFAULT 0,
  `menu_level` smallint NOT NULL,
  `if_xy` smallint NULL DEFAULT 0,
  `head_count` int NULL DEFAULT 0,
  `sort_order` int NULL DEFAULT 0,
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`menu_id`) USING BTREE,
  INDEX `idx_org_menu_parent`(`menu_parent_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 7 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '缁勭粐鏋舵瀯琛? ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of t_org_menu
-- ----------------------------
INSERT INTO `t_org_menu` VALUES (1, '鍏板窞澶у', 0, 1, 0, 35000, 1, '2026-03-22 10:58:54', '2026-03-22 10:58:54');
INSERT INTO `t_org_menu` VALUES (2, '璁＄畻鏈哄闄?, 1, 2, 1, 2800, 1, '2026-03-22 10:58:54', '2026-03-22 10:58:54');
INSERT INTO `t_org_menu` VALUES (3, '淇℃伅鎶€鏈腑蹇?, 1, 2, 0, 120, 2, '2026-03-22 10:58:54', '2026-03-22 10:58:54');
INSERT INTO `t_org_menu` VALUES (4, '鏁欏姟澶?, 1, 2, 0, 80, 3, '2026-03-22 10:58:54', '2026-03-22 10:58:54');
INSERT INTO `t_org_menu` VALUES (5, '鏁板涓庣粺璁″闄?, 1, 2, 1, 1500, 4, '2026-03-22 10:58:54', '2026-03-22 10:58:54');
INSERT INTO `t_org_menu` VALUES (6, '澶栧浗璇闄?, 1, 2, 1, 1200, 5, '2026-03-22 10:58:54', '2026-03-22 10:58:54');

-- ----------------------------
-- Table structure for t_platform_role
-- ----------------------------
DROP TABLE IF EXISTS `t_platform_role`;
CREATE TABLE `t_platform_role`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `role_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `description` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `permissions` json NOT NULL,
  `is_system` tinyint(1) NULL DEFAULT 0,
  `user_count` int NULL DEFAULT 0,
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_role_code`(`role_code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 6 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '骞冲彴瑙掕壊琛? ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of t_platform_role
-- ----------------------------
INSERT INTO `t_platform_role` VALUES (1, 'platform_admin', '骞冲彴绠＄悊鍛?, '鎷ユ湁鎵€鏈夋搷浣滄潈闄?, '[\"agent:read\", \"agent:create\", \"agent:update\", \"agent:delete\", \"agent:publish\", \"agent:audit\", \"skill:read\", \"skill:create\", \"skill:update\", \"skill:delete\", \"skill:publish\", \"skill:audit\", \"app:view\", \"app:create\", \"app:update\", \"app:delete\", \"dataset:read\", \"dataset:create\", \"dataset:update\", \"dataset:delete\", \"dataset:apply\", \"user:manage\", \"user:read\", \"user:create\", \"user:update\", \"user:delete\", \"role:read\", \"role:create\", \"role:update\", \"role:delete\", \"apikey:read\", \"apikey:create\", \"apikey:delete\", \"org:read\", \"org:create\", \"org:update\", \"org:delete\", \"system:config\", \"monitor:view\", \"audit:manage\"]', 1, 1, '2026-03-24 11:37:43', '2026-03-24 11:37:43');
INSERT INTO `t_platform_role` VALUES (2, 'dept_admin', '閮ㄩ棬绠＄悊鍛?, '鏈儴闂ㄨ祫婧愮鐞嗕笌鐢ㄦ埛鏌ョ湅', '[\"agent:read\", \"agent:create\", \"agent:update\", \"agent:audit\", \"skill:read\", \"skill:create\", \"skill:update\", \"skill:audit\", \"app:view\", \"dataset:read\", \"dataset:create\", \"dataset:update\", \"dataset:apply\", \"user:manage\", \"user:read\", \"user:create\", \"user:update\", \"role:read\", \"org:read\", \"monitor:view\"]', 1, 1, '2026-03-24 11:37:43', '2026-03-24 11:37:43');
INSERT INTO `t_platform_role` VALUES (3, 'developer', '寮€鍙戣€?, 'Agent/Skill 鍒涘缓涓庡彂甯?, '[\"agent:read\", \"agent:create\", \"agent:update\", \"agent:publish\", \"skill:read\", \"skill:create\", \"skill:update\", \"skill:publish\", \"app:view\", \"dataset:read\", \"dataset:apply\"]', 1, 1, '2026-03-24 11:37:43', '2026-03-24 11:37:43');
INSERT INTO `t_platform_role` VALUES (4, 'consumer', '娑堣垂鑰?, '鐩綍涓庡競鍦哄彧璇伙紙浜旂被璧勬簮娴忚锛沵cp 涓?skill 鍏辩敤 skill:read锛?, '[\"agent:read\", \"skill:read\", \"app:view\", \"dataset:read\"]', 1, 0, '2026-04-03 00:00:00', '2026-04-03 00:00:00');

-- ----------------------------
-- Table structure for t_rate_limit_rule
-- ----------------------------
DROP TABLE IF EXISTS `t_rate_limit_rule`;
CREATE TABLE `t_rate_limit_rule`  (
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `target` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `target_value` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `resource_scope` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `window_ms` bigint NOT NULL,
  `max_requests` int NOT NULL,
  `max_tokens` int NULL DEFAULT NULL,
  `burst_limit` int NULL DEFAULT NULL,
  `action` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `enabled` tinyint(1) NULL DEFAULT 1,
  `priority` int NULL DEFAULT 0,
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '闄愭祦瑙勫垯琛? ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of t_rate_limit_rule
-- ----------------------------
INSERT INTO `t_rate_limit_rule` VALUES ('rl-001', '鍏ㄥ眬榛樿闄愭祦', 'global', NULL, NULL, 60000, 100, 50000, 20, 'throttle', 1, 0, '2026-03-22 10:58:54', '2026-03-22 10:58:54');
INSERT INTO `t_rate_limit_rule` VALUES ('rl-002', '鍗曠敤鎴烽檺娴?, 'user', NULL, NULL, 60000, 30, 10000, 10, 'reject', 1, 10, '2026-03-22 10:58:54', '2026-03-22 10:58:54');
INSERT INTO `t_rate_limit_rule` VALUES ('rl-003', '绠＄悊鍛樿眮鍏?, 'role', 'platform_admin', NULL, 60000, 9999, 999999, 999, 'reject', 1, 20, '2026-03-22 10:58:54', '2026-03-22 10:58:54');

-- ----------------------------
-- Table structure for t_resource
-- ----------------------------
DROP TABLE IF EXISTS `t_resource`;
CREATE TABLE `t_resource`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `resource_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `resource_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `display_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL,
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'draft',
  `source_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `access_policy` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'open_platform',
  `view_count` bigint NOT NULL DEFAULT '0' COMMENT '浏览次数',
  `is_public` tinyint(1) NULL DEFAULT NULL COMMENT '是否公开',
  `service_detail_md` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '服务详情',
  `detail_json` json NULL COMMENT '扩展配置',
  `created_by` bigint NULL DEFAULT NULL,
  `deleted` smallint NULL DEFAULT 0,
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_resource_type_code`(`resource_type` ASC, `resource_code` ASC) USING BTREE,
  INDEX `idx_resource_type_status`(`resource_type` ASC, `status` ASC) USING BTREE,
  INDEX `idx_resource_update_time`(`update_time` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 32 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '缁熶竴璧勬簮涓昏〃' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of t_resource
-- ----------------------------
INSERT INTO `t_resource` (`id`, `resource_type`, `resource_code`, `display_name`, `description`, `status`, `source_type`, `created_by`, `deleted`, `create_time`, `update_time`) VALUES (1, 'agent', 'web-search', '鑱旂綉鎼滅储', '閫氳繃鎼滅储寮曟搸瀹炴椂妫€绱簰鑱旂綉淇℃伅骞舵眹鎬诲洖绛?, 'published', 'cloud', 1, 0, '2026-03-22 10:58:54', '2026-03-22 10:58:54');
INSERT INTO `t_resource` (`id`, `resource_type`, `resource_code`, `display_name`, `description`, `status`, `source_type`, `created_by`, `deleted`, `create_time`, `update_time`) VALUES (2, 'agent', 'smart-tutor', '鏅鸿兘澶囪鍔╂墜', '杈呭姪鏁欏笀蹇€熺敓鎴愭暀妗堛€佽浠跺ぇ绾插拰鏁欏娲诲姩璁捐', 'published', 'internal', 1, 0, '2026-03-22 10:58:54', '2026-03-22 10:58:54');
INSERT INTO `t_resource` (`id`, `resource_type`, `resource_code`, `display_name`, `description`, `status`, `source_type`, `created_by`, `deleted`, `create_time`, `update_time`) VALUES (3, 'agent', 'paper-polish', '璁烘枃娑﹁壊', '瀵瑰鏈鏂囪繘琛岃瑷€娑﹁壊銆佹牸寮忚鑼冨拰瀛︽湳琛ㄨ揪浼樺寲', 'published', 'cloud', 3, 0, '2026-03-22 10:58:54', '2026-03-22 10:58:54');
INSERT INTO `t_resource` (`id`, `resource_type`, `resource_code`, `display_name`, `description`, `status`, `source_type`, `created_by`, `deleted`, `create_time`, `update_time`) VALUES (4, 'agent', 'campus-qa', '鏍″洯闂瓟', '鍥炵瓟鏍″洯鐢熸椿銆佹暀鍔℃斂绛栥€佸姙浜嬫祦绋嬬瓑甯歌闂', 'published', 'internal', 1, 0, '2026-03-22 10:58:54', '2026-03-22 10:58:54');
INSERT INTO `t_resource` (`id`, `resource_type`, `resource_code`, `display_name`, `description`, `status`, `source_type`, `created_by`, `deleted`, `create_time`, `update_time`) VALUES (5, 'agent', 'image-gen', '鍥惧儚鐢熸垚', '鏍规嵁鏂囧瓧鎻忚堪鐢熸垚楂樿川閲忓浘鍍?, 'draft', 'cloud', 3, 0, '2026-03-22 10:58:54', '2026-03-22 10:58:54');
INSERT INTO `t_resource` (`id`, `resource_type`, `resource_code`, `display_name`, `description`, `status`, `source_type`, `created_by`, `deleted`, `create_time`, `update_time`) VALUES (6, 'agent', 'code-assistant', '浠ｇ爜鍔╂墜', '杈呭姪缂栧啓銆佽皟璇曞拰瑙ｉ噴浠ｇ爜锛屾敮鎸佸绉嶇紪绋嬭瑷€', 'published', 'cloud', 3, 0, '2026-03-22 10:58:54', '2026-03-22 10:58:54');
INSERT INTO `t_resource` (`id`, `resource_type`, `resource_code`, `display_name`, `description`, `status`, `source_type`, `created_by`, `deleted`, `create_time`, `update_time`) VALUES (8, 'mcp', 'lantu-mcp-server', '鍏版櫤閫?MCP Server', '鍏版櫤閫氬钩鍙板唴缃?MCP 宸ュ叿闆嗗悎锛屾彁渚涚煡璇嗗簱妫€绱€佹枃妗ｇ敓鎴愮瓑鑳藉姏', 'published', 'internal', 1, 0, '2026-03-22 10:58:54', '2026-03-25 09:59:40');
INSERT INTO `t_resource` (`id`, `resource_type`, `resource_code`, `display_name`, `description`, `status`, `source_type`, `created_by`, `deleted`, `create_time`, `update_time`) VALUES (9, 'mcp', 'local-kb-search', '鏈湴鐭ヨ瘑搴撴悳绱?, '鍦ㄦ牎鍐呯煡璇嗗簱涓绱㈢浉鍏虫枃妗ｅ拰淇℃伅', 'published', 'internal', 1, 0, '2026-03-22 10:58:54', '2026-03-22 10:58:54');
INSERT INTO `t_resource` (`id`, `resource_type`, `resource_code`, `display_name`, `description`, `status`, `source_type`, `created_by`, `deleted`, `create_time`, `update_time`) VALUES (10, 'mcp', 'word-gen', 'Word鏂囨。鐢熸垚', '鏍规嵁杈撳叆鍐呭鑷姩鐢熸垚瑙勮寖鐨?Word 鏂囨。', 'published', 'internal', 1, 0, '2026-03-22 10:58:54', '2026-03-22 10:58:54');
INSERT INTO `t_resource` (`id`, `resource_type`, `resource_code`, `display_name`, `description`, `status`, `source_type`, `created_by`, `deleted`, `create_time`, `update_time`) VALUES (11, 'mcp', 'ppt-gen', 'PPT鐢熸垚', '鏍规嵁涓婚鍜屽ぇ绾茶嚜鍔ㄧ敓鎴愭紨绀烘枃绋?, 'published', 'internal', 1, 0, '2026-03-22 10:58:54', '2026-03-22 10:58:54');
INSERT INTO `t_resource` (`id`, `resource_type`, `resource_code`, `display_name`, `description`, `status`, `source_type`, `created_by`, `deleted`, `create_time`, `update_time`) VALUES (12, 'mcp', 'schedule-query', '鏃ョ▼鏌ヨ', '鏌ヨ涓汉鎴栭儴闂ㄧ殑鏃ョ▼瀹夋帓', 'published', 'internal', 1, 0, '2026-03-22 10:58:54', '2026-03-22 10:58:54');
INSERT INTO `t_resource` (`id`, `resource_type`, `resource_code`, `display_name`, `description`, `status`, `source_type`, `created_by`, `deleted`, `create_time`, `update_time`) VALUES (13, 'mcp', 'data-analysis', '鏁版嵁鍒嗘瀽宸ュ叿', '瀵逛笂浼犵殑缁撴瀯鍖栨暟鎹繘琛岀粺璁″垎鏋愬拰鍙鍖?, 'published', 'cloud', 3, 0, '2026-03-22 10:58:54', '2026-03-22 10:58:54');
INSERT INTO `t_resource` (`id`, `resource_type`, `resource_code`, `display_name`, `description`, `status`, `source_type`, `created_by`, `deleted`, `create_time`, `update_time`) VALUES (14, 'mcp', 'translate', '澶氳瑷€缈昏瘧', '鏀寔涓嫳鏃ラ煩绛夊璇█浜掕瘧', 'published', 'cloud', 1, 0, '2026-03-22 10:58:54', '2026-03-22 10:58:54');
INSERT INTO `t_resource` (`id`, `resource_type`, `resource_code`, `display_name`, `description`, `status`, `source_type`, `created_by`, `deleted`, `create_time`, `update_time`) VALUES (15, 'mcp', 'ocr-recognize', 'OCR 鏂囧瓧璇嗗埆', '璇嗗埆鍥剧墖涓殑鏂囧瓧鍐呭', 'deprecated', 'cloud', 3, 0, '2026-03-22 10:58:54', '2026-03-25 09:28:55');
INSERT INTO `t_resource` (`id`, `resource_type`, `resource_code`, `display_name`, `description`, `status`, `source_type`, `created_by`, `deleted`, `create_time`, `update_time`) VALUES (23, 'app', 'campus-card', '鏍″洯涓€鍗￠€氭煡璇?, '鏌ヨ鏍″洯鍗′綑棰濄€佹秷璐硅褰曞拰鍏呭€?, 'published', 'internal', 1, 0, '2026-03-22 10:58:54', '2026-03-22 10:58:54');
INSERT INTO `t_resource` (`id`, `resource_type`, `resource_code`, `display_name`, `description`, `status`, `source_type`, `created_by`, `deleted`, `create_time`, `update_time`) VALUES (24, 'app', 'library-seat', '鍥句功棣嗗骇浣嶉绾?, '鍦ㄧ嚎棰勭害鍥句功棣嗚嚜涔犲骇浣?, 'published', 'internal', 1, 0, '2026-03-22 10:58:54', '2026-03-22 10:58:54');
INSERT INTO `t_resource` (`id`, `resource_type`, `resource_code`, `display_name`, `description`, `status`, `source_type`, `created_by`, `deleted`, `create_time`, `update_time`) VALUES (25, 'app', 'course-table', '璇捐〃鏌ヨ', '鏌ョ湅涓汉璇剧▼琛ㄥ拰鑰冭瘯瀹夋帓', 'published', 'internal', 1, 0, '2026-03-22 10:58:54', '2026-03-22 10:58:54');
INSERT INTO `t_resource` (`id`, `resource_type`, `resource_code`, `display_name`, `description`, `status`, `source_type`, `created_by`, `deleted`, `create_time`, `update_time`) VALUES (26, 'dataset', 'cs-papers-2026', '璁＄畻鏈鸿鏂囧簱2026', '2026骞村害璁＄畻鏈洪鍩熸牳蹇冩湡鍒婅鏂囬泦', 'deprecated', 'knowledge', 3, 0, '2026-03-22 10:58:54', '2026-03-25 09:28:38');
INSERT INTO `t_resource` (`id`, `resource_type`, `resource_code`, `display_name`, `description`, `status`, `source_type`, `created_by`, `deleted`, `create_time`, `update_time`) VALUES (27, 'dataset', 'student-scores', '瀛︾敓鎴愮哗鏁版嵁', '杩戜笁骞村叏鏍℃湰绉戠敓鎴愮哗缁熻鏁版嵁锛堣劚鏁忥級', 'published', 'department', 1, 0, '2026-03-22 10:58:54', '2026-03-22 10:58:54');
INSERT INTO `t_resource` (`id`, `resource_type`, `resource_code`, `display_name`, `description`, `status`, `source_type`, `created_by`, `deleted`, `create_time`, `update_time`) VALUES (28, 'dataset', 'campus-news-corpus', '鏍″洯鏂伴椈璇枡', '鍏板窞澶у瀹樼綉鏂伴椈璇枡搴擄紙鐢ㄤ簬NLP璁粌锛?, 'published', 'knowledge', 3, 0, '2026-03-22 10:58:54', '2026-03-22 10:58:54');
INSERT INTO `t_resource` (`id`, `resource_type`, `resource_code`, `display_name`, `description`, `status`, `source_type`, `created_by`, `deleted`, `create_time`, `update_time`) VALUES (31, 'skill', 'demo-skill-pack', '绀轰緥 Anthropic 鎶€鑳藉寘', '婕旂ず鐢ㄥ彲涓嬭浇鎶€鑳藉寘锛堥潪杩滅▼ MCP锛?, 'published', 'internal', 1, 0, '2026-03-31 12:00:00', '2026-03-31 12:00:00');
INSERT INTO `t_resource` (`id`, `resource_type`, `resource_code`, `display_name`, `description`, `status`, `source_type`, `created_by`, `deleted`, `create_time`, `update_time`) VALUES (29, 'mcp', 'smoke-mcp-1774368649', 'Smoke MCP', 'smoke test', 'published', 'internal', 1, 0, '2026-03-25 00:10:50', '2026-03-25 00:10:50');
INSERT INTO `t_resource` (`id`, `resource_type`, `resource_code`, `display_name`, `description`, `status`, `source_type`, `created_by`, `deleted`, `create_time`, `update_time`) VALUES (30, 'mcp', 'smoke2-mcp-1774368774', 'Smoke2 MCP', 'smoke test 2', 'published', 'internal', 1, 0, '2026-03-25 00:12:55', '2026-03-25 00:12:55');

-- ----------------------------
-- Table structure for t_resource_agent_ext
-- ----------------------------
DROP TABLE IF EXISTS `t_resource_agent_ext`;
CREATE TABLE `t_resource_agent_ext`  (
  `resource_id` bigint NOT NULL,
  `agent_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `mode` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `spec_json` json NOT NULL,
  `is_public` tinyint(1) NULL DEFAULT 0,
  `hidden` tinyint(1) NULL DEFAULT 0,
  `max_concurrency` int NULL DEFAULT 10,
  `max_steps` int NULL DEFAULT NULL,
  `temperature` decimal(3, 2) NULL DEFAULT NULL,
  `system_prompt` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL,
  `service_detail_md` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '浠嬬粛 Markdown锛堥€夊～锛?,
  `featured` tinyint(1) NULL DEFAULT 0,
  `rating_avg` decimal(3, 2) NULL DEFAULT 0.00,
  `rating_count` int NULL DEFAULT 0,
  PRIMARY KEY (`resource_id`) USING BTREE,
  CONSTRAINT `fk_resource_agent_ext_resource` FOREIGN KEY (`resource_id`) REFERENCES `t_resource` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '璧勬簮-Agent鎵╁睍琛? ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of t_resource_agent_ext
-- ----------------------------
INSERT INTO `t_resource_agent_ext` VALUES (1, 'http_api', 'SUBAGENT', '{\"url\": \"https://api.search.com/v1\", \"timeout\": 30}', 1, 0, 20, NULL, NULL, '浣犳槸涓€涓仈缃戞悳绱㈠姪鎵嬶紝璇锋牴鎹敤鎴烽棶棰樻悳绱㈡渶鏂颁俊鎭苟褰掔撼鍥炵瓟銆?, NULL, 0, 0.00, 0);
INSERT INTO `t_resource_agent_ext` VALUES (2, 'http_api', 'SUBAGENT', '{\"url\": \"http://ai.lzu.edu.cn/tutor\", \"timeout\": 60}', 1, 0, 10, NULL, NULL, '浣犳槸涓€涓暀瀛﹀姪鎵嬶紝璇锋牴鎹绋嬪悕绉板拰鏁欏鐩爣鐢熸垚璇︾粏鏁欐銆?, NULL, 0, 0.00, 0);
INSERT INTO `t_resource_agent_ext` VALUES (3, 'http_api', 'SUBAGENT', '{\"url\": \"https://api.polish.com/v1\", \"timeout\": 90}', 1, 0, 15, NULL, NULL, '浣犳槸涓€涓鏈啓浣滃姪鎵嬶紝璇峰杈撳叆鐨勮鏂囨钀借繘琛屾鼎鑹层€?, NULL, 0, 0.00, 0);
INSERT INTO `t_resource_agent_ext` VALUES (4, 'builtin', 'SUBAGENT', '{\"url\": \"http://ai.lzu.edu.cn/qa\", \"timeout\": 15}', 1, 0, 30, NULL, NULL, '浣犳槸鍏板窞澶у鐨勬牎鍥姪鎵嬶紝璇峰洖绛斿悓瀛︿滑鐨勬牎鍥敓娲婚棶棰樸€?, NULL, 0, 0.00, 0);
INSERT INTO `t_resource_agent_ext` VALUES (5, 'http_api', 'SUBAGENT', '{\"url\": \"https://api.image.com/v1\", \"timeout\": 120}', 0, 0, 5, NULL, NULL, '浣犳槸涓€涓浘鍍忕敓鎴愬姪鎵嬶紝璇锋牴鎹敤鎴风殑鎻忚堪鐢熸垚瀵瑰簲鐨勫浘鐗囥€?, NULL, 0, 0.00, 0);
INSERT INTO `t_resource_agent_ext` VALUES (6, 'http_api', 'SUBAGENT', '{\"url\": \"https://api.code.com/v1\", \"timeout\": 60}', 1, 0, 20, NULL, NULL, '浣犳槸涓€涓紪绋嬪姪鎵嬶紝璇峰府鍔╃敤鎴风紪鍐欏拰璋冭瘯浠ｇ爜銆?, NULL, 0, 0.00, 0);

-- ----------------------------
-- Table structure for t_resource_app_ext
-- ----------------------------
DROP TABLE IF EXISTS `t_resource_app_ext`;
CREATE TABLE `t_resource_app_ext`  (
  `resource_id` bigint NOT NULL,
  `app_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `embed_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `icon` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `screenshots` json NULL,
  `is_public` tinyint(1) NULL DEFAULT 0,
  `agent_exposure` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '妞ょ敻娼伴崹?Agent 閺嗘挳婀惰ぐ銏♀偓渚婄幢unified_agent 鐞涖劎銇氱痪鍐插弳 Agent 鐟欏棗娴?,
  `agent_delivery_mode` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '妞ょ敻娼伴崹?Agent 娴溿倕绮Ο鈥崇础閿涘苯缍嬮崜宥勭矌 page',
  `service_detail_md` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '搴旂敤浠嬬粛 Markdown锛堥€夊～锛?,
  PRIMARY KEY (`resource_id`) USING BTREE,
  CONSTRAINT `fk_resource_app_ext_resource` FOREIGN KEY (`resource_id`) REFERENCES `t_resource` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '璧勬簮-App鎵╁睍琛? ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of t_resource_app_ext
-- ----------------------------
INSERT INTO `t_resource_app_ext` VALUES (23, 'https://card.lzu.edu.cn', 'iframe', NULL, '[]', 1, NULL, NULL, NULL);
INSERT INTO `t_resource_app_ext` VALUES (24, 'https://lib.lzu.edu.cn/seat', 'iframe', NULL, '[]', 1, NULL, NULL, NULL);
INSERT INTO `t_resource_app_ext` VALUES (25, 'https://jwc.lzu.edu.cn/course', 'micro_frontend', NULL, '[]', 1, NULL, NULL, NULL);

-- ----------------------------
-- Table structure for t_resource_circuit_breaker
-- ----------------------------
DROP TABLE IF EXISTS `t_resource_circuit_breaker`;
CREATE TABLE `t_resource_circuit_breaker`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `resource_id` bigint NOT NULL,
  `resource_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `resource_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `display_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `current_state` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'CLOSED',
  `failure_threshold` int NULL DEFAULT 5,
  `open_duration_sec` int NULL DEFAULT 60,
  `half_open_max_calls` int NULL DEFAULT 3,
  `fallback_resource_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `fallback_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL,
  `last_opened_at` datetime NULL DEFAULT NULL,
  `success_count` bigint NULL DEFAULT 0,
  `failure_count` bigint NULL DEFAULT 0,
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_resource_cb_resource`(`resource_id` ASC) USING BTREE,
  INDEX `idx_resource_cb_state`(`current_state` ASC) USING BTREE,
  INDEX `idx_resource_cb_type_code`(`resource_type` ASC, `resource_code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '璧勬簮鐔旀柇閰嶇疆琛? ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of t_resource_circuit_breaker
-- ----------------------------
INSERT INTO `t_resource_circuit_breaker` VALUES (1, 4, 'agent', 'campus-qa', '鏍″洯闂瓟', 'HALF_OPEN', 5, 60, 3, NULL, '鏍″洯闂瓟鏈嶅姟鏆傛椂涓嶅彲鐢紝璇风◢鍚庨噸璇?, NULL, 25000, 180, '2026-03-22 10:58:54', '2026-03-22 10:58:54');
INSERT INTO `t_resource_circuit_breaker` VALUES (2, 5, 'agent', 'image-gen', '鍥惧儚鐢熸垚', 'CLOSED', 5, 60, 3, NULL, '鍥惧儚鐢熸垚鏈嶅姟鏆傛椂涓嶅彲鐢紝璇风◢鍚庨噸璇?, NULL, 0, 0, '2026-03-22 10:58:54', '2026-03-22 10:58:54');

-- ----------------------------
-- Table structure for t_resource_dataset_ext
-- ----------------------------
DROP TABLE IF EXISTS `t_resource_dataset_ext`;
CREATE TABLE `t_resource_dataset_ext`  (
  `resource_id` bigint NOT NULL,
  `data_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `format` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `record_count` bigint NULL DEFAULT 0,
  `file_size` bigint NULL DEFAULT 0,
  `tags` json NULL,
  `is_public` tinyint(1) NULL DEFAULT 0,
  `service_detail_md` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '鏁版嵁闆嗕粙缁?Markdown锛堥€夊～锛?,
  PRIMARY KEY (`resource_id`) USING BTREE,
  CONSTRAINT `fk_resource_dataset_ext_resource` FOREIGN KEY (`resource_id`) REFERENCES `t_resource` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '璧勬簮-Dataset鎵╁睍琛? ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of t_resource_dataset_ext
-- ----------------------------
INSERT INTO `t_resource_dataset_ext` VALUES (26, 'document', 'pdf', 12500, 5368709120, '[\"璁烘枃\", \"璁＄畻鏈篭", \"瀛︽湳\"]', 0, NULL);
INSERT INTO `t_resource_dataset_ext` VALUES (27, 'structured', 'csv', 856000, 134217728, '[\"鎴愮哗\", \"缁熻\", \"鏁欏姟\"]', 0, NULL);
INSERT INTO `t_resource_dataset_ext` VALUES (28, 'document', 'json', 45000, 268435456, '[\"鏂伴椈\", \"NLP\", \"璇枡\"]', 1, NULL);

-- ----------------------------
-- Table structure for t_resource_health_config
-- ----------------------------
DROP TABLE IF EXISTS `t_resource_health_config`;
CREATE TABLE `t_resource_health_config`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `resource_id` bigint NOT NULL,
  `resource_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `resource_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `display_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `check_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'http',
  `check_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `interval_sec` int NULL DEFAULT 30,
  `healthy_threshold` int NULL DEFAULT 3,
  `timeout_sec` int NULL DEFAULT 10,
  `health_status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'healthy',
  `last_check_time` datetime NULL DEFAULT NULL,
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_resource_health_resource`(`resource_id` ASC) USING BTREE,
  INDEX `idx_resource_health_type_status`(`resource_type` ASC, `health_status` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '璧勬簮鍋ュ悍妫€鏌ラ厤缃〃' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of t_resource_health_config
-- ----------------------------
INSERT INTO `t_resource_health_config` VALUES (1, 1, 'agent', 'web-search', '鑱旂綉鎼滅储', 'http', 'https://api.search.com/health', 30, 3, 10, 'degraded', '2026-03-25 17:48:10', '2026-03-22 10:58:54', '2026-03-25 17:48:10');
INSERT INTO `t_resource_health_config` VALUES (2, 2, 'agent', 'smart-tutor', '鏅鸿兘澶囪鍔╂墜', 'http', 'http://ai.lzu.edu.cn/tutor/health', 30, 3, 10, 'degraded', '2026-03-25 17:48:15', '2026-03-22 10:58:54', '2026-03-25 17:48:14');
INSERT INTO `t_resource_health_config` VALUES (3, 4, 'agent', 'campus-qa', '鏍″洯闂瓟', 'http', 'http://ai.lzu.edu.cn/qa/health', 30, 3, 10, 'degraded', '2026-03-25 17:48:20', '2026-03-22 10:58:54', '2026-03-25 17:48:19');

-- ----------------------------
-- Table structure for t_resource_mcp_ext
-- ----------------------------
DROP TABLE IF EXISTS `t_resource_mcp_ext`;
CREATE TABLE `t_resource_mcp_ext`  (
  `resource_id` bigint NOT NULL,
  `endpoint` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `protocol` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'http',
  `auth_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'none',
  `auth_config` json NULL,
  `service_detail_md` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '鏈嶅姟璇︽儏 Markdown锛堥€夊～锛?,
  PRIMARY KEY (`resource_id`) USING BTREE,
  CONSTRAINT `fk_resource_mcp_ext_resource` FOREIGN KEY (`resource_id`) REFERENCES `t_resource` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '璧勬簮-MCP鎵╁睍琛? ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of t_resource_mcp_ext
-- ----------------------------
INSERT INTO `t_resource_mcp_ext` VALUES (8, 'http://ai.lzu.edu.cn/mcp', 'mcp', 'none', '{\"url\": \"http://ai.lzu.edu.cn/mcp\", \"timeout\": 30}', NULL);
INSERT INTO `t_resource_mcp_ext` VALUES (9, 'http://ai.lzu.edu.cn/mcp/kb', 'mcp', 'none', '{\"url\": \"http://ai.lzu.edu.cn/mcp/kb\", \"timeout\": 10}', NULL);
INSERT INTO `t_resource_mcp_ext` VALUES (10, 'http://ai.lzu.edu.cn/mcp/word', 'mcp', 'none', '{\"url\": \"http://ai.lzu.edu.cn/mcp/word\", \"timeout\": 30}', NULL);
INSERT INTO `t_resource_mcp_ext` VALUES (11, 'http://ai.lzu.edu.cn/mcp/ppt', 'mcp', 'none', '{\"url\": \"http://ai.lzu.edu.cn/mcp/ppt\", \"timeout\": 60}', NULL);
INSERT INTO `t_resource_mcp_ext` VALUES (12, 'http://ai.lzu.edu.cn/mcp/schedule', 'mcp', 'none', '{\"url\": \"http://ai.lzu.edu.cn/mcp/schedule\", \"timeout\": 5}', NULL);
INSERT INTO `t_resource_mcp_ext` VALUES (13, 'https://api.analysis.com/v1', 'mcp', 'none', '{\"url\": \"https://api.analysis.com/v1\", \"timeout\": 30}', NULL);
INSERT INTO `t_resource_mcp_ext` VALUES (14, 'https://api.translate.com/v1', 'http', 'none', '{\"url\": \"https://api.translate.com/v1\", \"timeout\": 10}', NULL);
INSERT INTO `t_resource_mcp_ext` VALUES (15, 'https://api.ocr.com/v1', 'http', 'none', '{\"url\": \"https://api.ocr.com/v1\", \"timeout\": 15}', NULL);
INSERT INTO `t_resource_mcp_ext` VALUES (29, 'http://localhost:9000/mcp', 'mcp', 'none', '{\"method\": \"tools/call\"}', NULL);
INSERT INTO `t_resource_mcp_ext` VALUES (30, 'http://localhost:9000/mcp', 'mcp', 'none', '{\"method\": \"tools/call\"}', NULL);

-- Table structure for t_resource_relation
-- ----------------------------
DROP TABLE IF EXISTS `t_resource_relation`;
CREATE TABLE `t_resource_relation`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `from_resource_id` bigint NOT NULL,
  `to_resource_id` bigint NOT NULL,
  `relation_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_relation_from`(`from_resource_id` ASC) USING BTREE,
  INDEX `idx_relation_to`(`to_resource_id` ASC) USING BTREE,
  INDEX `idx_relation_type`(`relation_type` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 11 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '璧勬簮鍏崇郴琛? ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of t_resource_relation
-- ----------------------------
INSERT INTO `t_resource_relation` VALUES (1, 26, 3, 'dataset_binds_agent', '2026-03-24 12:13:38');
INSERT INTO `t_resource_relation` VALUES (2, 28, 1, 'dataset_binds_agent', '2026-03-24 12:13:38');
-- skill_child_of 宸插簾寮冿紙鎶€鑳藉寘涓嶄笌 MCP 寤虹珛瀛愬叧绯伙級锛涜 incremental V26

-- ----------------------------
-- Table structure for t_resource_skill_ext
-- ----------------------------
DROP TABLE IF EXISTS `t_resource_skill_ext`;
CREATE TABLE `t_resource_skill_ext`  (
  `resource_id` bigint NOT NULL,
  `skill_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '鎶€鑳藉寘鏍煎紡 anthropic_v1 / folder_v1',
  `artifact_uri` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '鎶€鑳藉寘 URI',
  `artifact_sha256` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '鍖?SHA-256 hex',
  `manifest_json` json NULL COMMENT 'manifest JSON',
  `entry_doc` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '鍏ュ彛鏂囨。鐩稿璺緞濡?SKILL.md',
  `mode` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'TOOL',
  `parent_resource_id` bigint NULL DEFAULT NULL,
  `display_template` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `spec_json` json NULL COMMENT '鍙€夐檮鍔犲厓鏁版嵁锛岄潪杩滅▼璋冪敤閰嶇疆',
  `parameters_schema` json NULL,
  `is_public` tinyint(1) NULL DEFAULT 0,
  `max_concurrency` int NULL DEFAULT 10,
  `pack_validation_status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'none' COMMENT 'none/pending/valid/invalid',
  `pack_validated_at` datetime NULL DEFAULT NULL,
  `pack_validation_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL,
  `skill_root_path` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'zip鍐呮妧鑳芥牴瀛愮洰褰曪紝璇箟鏍￠獙浣滅敤鍩?,
  `service_detail_md` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '鎶€鑳戒粙缁?Markdown锛堥€夊～锛?,
  PRIMARY KEY (`resource_id`) USING BTREE,
  INDEX `idx_skill_ext_parent`(`parent_resource_id` ASC) USING BTREE,
  INDEX `idx_skill_pack_validation`(`pack_validation_status` ASC) USING BTREE,
  CONSTRAINT `fk_resource_skill_ext_resource` FOREIGN KEY (`resource_id`) REFERENCES `t_resource` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '璧勬簮-Skill鎵╁睍琛? ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of t_resource_skill_ext
-- ----------------------------
INSERT INTO `t_resource_skill_ext` VALUES (31, 'anthropic_v1', 'https://example.com/skills/demo-skill-pack.zip', NULL, '{\"name\": \"demo-skill-pack\", \"version\": \"1.0.0\"}', 'SKILL.md', NULL, NULL, NULL, NULL, NULL, 1, NULL, 'valid', NULL, NULL, NULL, NULL);

-- ----------------------------
-- Table structure for t_resource_tag_rel
-- ----------------------------
DROP TABLE IF EXISTS `t_resource_tag_rel`;
CREATE TABLE `t_resource_tag_rel`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `resource_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `resource_id` bigint NOT NULL,
  `tag_id` bigint NOT NULL,
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_resource_tag`(`resource_type` ASC, `resource_id` ASC, `tag_id` ASC) USING BTREE,
  INDEX `idx_tag_id`(`tag_id` ASC) USING BTREE,
  CONSTRAINT `fk_resource_tag_tag` FOREIGN KEY (`tag_id`) REFERENCES `t_tag` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 11 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '璧勬簮鏍囩鍏宠仈琛? ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of t_resource_tag_rel
-- ----------------------------
INSERT INTO `t_resource_tag_rel` VALUES (1, 'agent', 1, 4, '2026-03-22 10:58:54');
INSERT INTO `t_resource_tag_rel` VALUES (2, 'agent', 1, 9, '2026-03-22 10:58:54');
INSERT INTO `t_resource_tag_rel` VALUES (3, 'agent', 2, 1, '2026-03-22 10:58:54');
INSERT INTO `t_resource_tag_rel` VALUES (4, 'agent', 3, 2, '2026-03-22 10:58:54');
INSERT INTO `t_resource_tag_rel` VALUES (5, 'agent', 4, 8, '2026-03-22 10:58:54');
INSERT INTO `t_resource_tag_rel` VALUES (6, 'agent', 6, 9, '2026-03-22 10:58:54');
INSERT INTO `t_resource_tag_rel` VALUES (7, 'mcp', 9, 3, '2026-03-22 10:58:54');
INSERT INTO `t_resource_tag_rel` VALUES (8, 'mcp', 14, 5, '2026-03-22 10:58:54');
INSERT INTO `t_resource_tag_rel` VALUES (9, 'dataset', 1, 7, '2026-03-22 10:58:54');
INSERT INTO `t_resource_tag_rel` VALUES (10, 'dataset', 1, 2, '2026-03-22 10:58:54');

-- ----------------------------
-- Table structure for t_resource_version
-- ----------------------------
DROP TABLE IF EXISTS `t_resource_version`;
CREATE TABLE `t_resource_version`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `resource_id` bigint NOT NULL,
  `version` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'active',
  `is_current` tinyint(1) NULL DEFAULT 0,
  `snapshot_json` json NULL,
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_resource_version`(`resource_id` ASC, `version` ASC) USING BTREE,
  INDEX `idx_resource_current`(`resource_id` ASC, `is_current` ASC, `create_time` ASC) USING BTREE,
  CONSTRAINT `fk_resource_version_resource` FOREIGN KEY (`resource_id`) REFERENCES `t_resource` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 37 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '璧勬簮鐗堟湰琛? ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of t_resource_version
-- ----------------------------
INSERT INTO `t_resource_version` VALUES (1, 1, 'v1', 'active', 1, '{\"spec\": {\"url\": \"https://api.search.com/v1\", \"timeout\": 30}, \"status\": \"published\", \"endpoint\": \"https://api.search.com/v1\", \"invokeType\": \"rest\", \"displayName\": \"鑱旂綉鎼滅储\", \"resourceCode\": \"web-search\"}', '2026-03-24 21:23:03');
INSERT INTO `t_resource_version` VALUES (2, 2, 'v1', 'active', 1, '{\"spec\": {\"url\": \"http://ai.lzu.edu.cn/tutor\", \"timeout\": 60}, \"status\": \"published\", \"endpoint\": \"http://ai.lzu.edu.cn/tutor\", \"invokeType\": \"rest\", \"displayName\": \"鏅鸿兘澶囪鍔╂墜\", \"resourceCode\": \"smart-tutor\"}', '2026-03-24 21:23:03');
INSERT INTO `t_resource_version` VALUES (3, 3, 'v1', 'active', 1, '{\"spec\": {\"url\": \"https://api.polish.com/v1\", \"timeout\": 90}, \"status\": \"published\", \"endpoint\": \"https://api.polish.com/v1\", \"invokeType\": \"rest\", \"displayName\": \"璁烘枃娑﹁壊\", \"resourceCode\": \"paper-polish\"}', '2026-03-24 21:23:03');
INSERT INTO `t_resource_version` VALUES (4, 4, 'v1', 'active', 1, '{\"spec\": {\"url\": \"http://ai.lzu.edu.cn/qa\", \"timeout\": 15}, \"status\": \"published\", \"endpoint\": \"http://ai.lzu.edu.cn/qa\", \"invokeType\": \"rest\", \"displayName\": \"鏍″洯闂瓟\", \"resourceCode\": \"campus-qa\"}', '2026-03-24 21:23:03');
INSERT INTO `t_resource_version` VALUES (5, 5, 'v1', 'active', 1, '{\"spec\": {\"url\": \"https://api.image.com/v1\", \"timeout\": 120}, \"status\": \"draft\", \"endpoint\": \"https://api.image.com/v1\", \"invokeType\": \"rest\", \"displayName\": \"鍥惧儚鐢熸垚\", \"resourceCode\": \"image-gen\"}', '2026-03-24 21:23:03');
INSERT INTO `t_resource_version` VALUES (6, 6, 'v1', 'active', 1, '{\"spec\": {\"url\": \"https://api.code.com/v1\", \"timeout\": 60}, \"status\": \"published\", \"endpoint\": \"https://api.code.com/v1\", \"invokeType\": \"rest\", \"displayName\": \"浠ｇ爜鍔╂墜\", \"resourceCode\": \"code-assistant\"}', '2026-03-24 21:23:03');
INSERT INTO `t_resource_version` VALUES (7, 8, 'v1', 'active', 1, '{\"resourceType\": \"mcp\", \"resourceCode\": \"lantu-mcp-server\", \"displayName\": \"鍏版櫤閫?MCP Server\", \"status\": \"published\", \"invokeType\": \"mcp\", \"endpoint\": \"http://ai.lzu.edu.cn/mcp\", \"spec\": {\"url\": \"http://ai.lzu.edu.cn/mcp\", \"timeout\": 30}}', '2026-03-24 21:23:03');
INSERT INTO `t_resource_version` VALUES (8, 9, 'v1', 'active', 1, '{\"resourceType\": \"mcp\", \"resourceCode\": \"local-kb-search\", \"displayName\": \"鏈湴鐭ヨ瘑搴撴悳绱", \"status\": \"published\", \"invokeType\": \"mcp\", \"endpoint\": \"http://ai.lzu.edu.cn/mcp/kb\", \"spec\": {\"url\": \"http://ai.lzu.edu.cn/mcp/kb\", \"timeout\": 10}}', '2026-03-24 21:23:03');
INSERT INTO `t_resource_version` VALUES (9, 10, 'v1', 'active', 1, '{\"resourceType\": \"mcp\", \"resourceCode\": \"word-gen\", \"displayName\": \"Word鏂囨。鐢熸垚\", \"status\": \"published\", \"invokeType\": \"mcp\", \"endpoint\": \"http://ai.lzu.edu.cn/mcp/word\", \"spec\": {\"url\": \"http://ai.lzu.edu.cn/mcp/word\", \"timeout\": 30}}', '2026-03-24 21:23:03');
INSERT INTO `t_resource_version` VALUES (10, 11, 'v1', 'active', 1, '{\"resourceType\": \"mcp\", \"resourceCode\": \"ppt-gen\", \"displayName\": \"PPT鐢熸垚\", \"status\": \"published\", \"invokeType\": \"mcp\", \"endpoint\": \"http://ai.lzu.edu.cn/mcp/ppt\", \"spec\": {\"url\": \"http://ai.lzu.edu.cn/mcp/ppt\", \"timeout\": 60}}', '2026-03-24 21:23:03');
INSERT INTO `t_resource_version` VALUES (11, 12, 'v1', 'active', 1, '{\"resourceType\": \"mcp\", \"resourceCode\": \"schedule-query\", \"displayName\": \"鏃ョ▼鏌ヨ\", \"status\": \"published\", \"invokeType\": \"mcp\", \"endpoint\": \"http://ai.lzu.edu.cn/mcp/schedule\", \"spec\": {\"url\": \"http://ai.lzu.edu.cn/mcp/schedule\", \"timeout\": 5}}', '2026-03-24 21:23:03');
INSERT INTO `t_resource_version` VALUES (12, 13, 'v1', 'active', 1, '{\"resourceType\": \"mcp\", \"resourceCode\": \"data-analysis\", \"displayName\": \"鏁版嵁鍒嗘瀽宸ュ叿\", \"status\": \"published\", \"invokeType\": \"mcp\", \"endpoint\": \"https://api.analysis.com/v1\", \"spec\": {\"url\": \"https://api.analysis.com/v1\", \"timeout\": 30}}', '2026-03-24 21:23:03');
INSERT INTO `t_resource_version` VALUES (13, 14, 'v1', 'active', 1, '{\"resourceType\": \"mcp\", \"resourceCode\": \"translate\", \"displayName\": \"澶氳瑷€缈昏瘧\", \"status\": \"published\", \"invokeType\": \"http\", \"endpoint\": \"https://api.translate.com/v1\", \"spec\": {\"url\": \"https://api.translate.com/v1\", \"timeout\": 10}}', '2026-03-24 21:23:03');
INSERT INTO `t_resource_version` VALUES (14, 15, 'v1', 'active', 1, '{\"resourceType\": \"mcp\", \"resourceCode\": \"ocr-recognize\", \"displayName\": \"OCR 鏂囧瓧璇嗗埆\", \"status\": \"deprecated\", \"invokeType\": \"http\", \"endpoint\": \"https://api.ocr.com/v1\", \"spec\": {\"url\": \"https://api.ocr.com/v1\", \"timeout\": 15}}', '2026-03-24 21:23:03');
INSERT INTO `t_resource_version` VALUES (15, 23, 'v1', 'active', 1, '{\"spec\": {\"embedType\": \"iframe\"}, \"status\": \"published\", \"endpoint\": \"https://card.lzu.edu.cn\", \"invokeType\": \"redirect\", \"displayName\": \"鏍″洯涓€鍗￠€氭煡璇", \"resourceCode\": \"campus-card\"}', '2026-03-24 21:23:03');
INSERT INTO `t_resource_version` VALUES (16, 24, 'v1', 'active', 1, '{\"spec\": {\"embedType\": \"iframe\"}, \"status\": \"published\", \"endpoint\": \"https://lib.lzu.edu.cn/seat\", \"invokeType\": \"redirect\", \"displayName\": \"鍥句功棣嗗骇浣嶉绾", \"resourceCode\": \"library-seat\"}', '2026-03-24 21:23:03');
INSERT INTO `t_resource_version` VALUES (17, 25, 'v1', 'active', 1, '{\"spec\": {\"embedType\": \"micro_frontend\"}, \"status\": \"published\", \"endpoint\": \"https://jwc.lzu.edu.cn/course\", \"invokeType\": \"redirect\", \"displayName\": \"璇捐〃鏌ヨ\", \"resourceCode\": \"course-table\"}', '2026-03-24 21:23:03');
INSERT INTO `t_resource_version` VALUES (18, 26, 'v1', 'active', 1, '{\"spec\": {\"tags\": [\"璁烘枃\", \"璁＄畻鏈篭", \"瀛︽湳\"], \"format\": \"pdf\", \"dataType\": \"document\", \"fileSize\": 5368709120, \"recordCount\": 12500}, \"status\": \"published\", \"invokeType\": \"metadata\", \"displayName\": \"璁＄畻鏈鸿鏂囧簱2026\", \"resourceCode\": \"cs-papers-2026\"}', '2026-03-24 21:23:03');
INSERT INTO `t_resource_version` VALUES (19, 27, 'v1', 'active', 1, '{\"spec\": {\"tags\": [\"鎴愮哗\", \"缁熻\", \"鏁欏姟\"], \"format\": \"csv\", \"dataType\": \"structured\", \"fileSize\": 134217728, \"recordCount\": 856000}, \"status\": \"published\", \"invokeType\": \"metadata\", \"displayName\": \"瀛︾敓鎴愮哗鏁版嵁\", \"resourceCode\": \"student-scores\"}', '2026-03-24 21:23:03');
INSERT INTO `t_resource_version` VALUES (20, 28, 'v1', 'active', 1, '{\"spec\": {\"tags\": [\"鏂伴椈\", \"NLP\", \"璇枡\"], \"format\": \"json\", \"dataType\": \"document\", \"fileSize\": 268435456, \"recordCount\": 45000}, \"status\": \"published\", \"invokeType\": \"metadata\", \"displayName\": \"鏍″洯鏂伴椈璇枡\", \"resourceCode\": \"campus-news-corpus\"}', '2026-03-24 21:23:03');
INSERT INTO `t_resource_version` VALUES (32, 29, 'v1', 'active', 1, '{\"spec\": {\"method\": \"tools/call\"}, \"status\": \"draft\", \"endpoint\": \"http://localhost:9000/mcp\", \"invokeType\": \"mcp\", \"description\": \"smoke test\", \"displayName\": \"Smoke MCP\", \"resourceCode\": \"smoke-mcp-1774368649\", \"resourceType\": \"mcp\"}', '2026-03-25 00:10:49');
INSERT INTO `t_resource_version` VALUES (33, 29, 'v2', 'active', 0, '{\"spec\": {\"method\": \"tools/call\"}, \"status\": \"published\", \"endpoint\": \"http://localhost:9000/mcp\", \"invokeType\": \"mcp\", \"description\": \"smoke test\", \"displayName\": \"Smoke MCP\", \"resourceCode\": \"smoke-mcp-1774368649\", \"resourceType\": \"mcp\"}', '2026-03-25 00:10:50');
INSERT INTO `t_resource_version` VALUES (34, 30, 'v1', 'active', 1, '{\"spec\": {\"method\": \"tools/call\"}, \"status\": \"draft\", \"endpoint\": \"http://localhost:9000/mcp\", \"invokeType\": \"mcp\", \"description\": \"smoke test 2\", \"displayName\": \"Smoke2 MCP\", \"resourceCode\": \"smoke2-mcp-1774368774\", \"resourceType\": \"mcp\"}', '2026-03-25 00:12:54');
INSERT INTO `t_resource_version` VALUES (35, 30, 'v2', 'active', 0, '{\"spec\": {\"method\": \"tools/call\"}, \"status\": \"published\", \"endpoint\": \"http://localhost:9000/mcp\", \"invokeType\": \"mcp\", \"description\": \"smoke test 2\", \"displayName\": \"Smoke2 MCP\", \"resourceCode\": \"smoke2-mcp-1774368774\", \"resourceType\": \"mcp\"}', '2026-03-25 00:12:55');
INSERT INTO `t_resource_version` VALUES (36, 31, 'v1', 'active', 1, '{\"resourceType\": \"skill\", \"packFormat\": \"anthropic_v1\", \"resourceCode\": \"demo-skill-pack\", \"displayName\": \"绀轰緥 Anthropic 鎶€鑳藉寘\", \"status\": \"published\", \"invokeType\": \"artifact\", \"endpoint\": \"https://example.com/skills/demo-skill-pack.zip\", \"spec\": {\"manifest\": {\"name\": \"demo-skill-pack\", \"version\": \"1.0.0\"}, \"entryDoc\": \"SKILL.md\"}}', '2026-03-31 12:00:00');

-- ----------------------------
-- Table structure for t_review
-- ----------------------------
DROP TABLE IF EXISTS `t_review`;
CREATE TABLE `t_review`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `target_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `target_id` bigint NOT NULL,
  `parent_id` bigint NULL DEFAULT NULL COMMENT '鐖惰瘎璁?id锛岄《绾т负 NULL',
  `user_id` bigint NOT NULL,
  `user_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `avatar` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `rating` smallint NOT NULL,
  `comment` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `helpful_count` int NULL DEFAULT 0,
  `deleted` smallint NULL DEFAULT 0,
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_review_target`(`target_type` ASC, `target_id` ASC) USING BTREE,
  INDEX `idx_review_parent`(`parent_id` ASC) USING BTREE,
  INDEX `idx_review_user`(`user_id` ASC) USING BTREE,
  CONSTRAINT `fk_review_parent` FOREIGN KEY (`parent_id`) REFERENCES `t_review` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT,
  CONSTRAINT `fk_review_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`user_id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '璇勮璇勫垎琛? ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of t_review
-- ----------------------------
INSERT INTO `t_review` VALUES (1, 'agent', 1, NULL, 4, '璧靛悓瀛?, NULL, 5, '鑱旂綉鎼滅储闈炲父濂界敤锛屽洖绛斿噯纭笖瀹炴椂鎬у己锛?, 3, 0, '2026-03-22 10:58:54');
INSERT INTO `t_review` VALUES (2, 'agent', 2, NULL, 4, '璧靛悓瀛?, NULL, 4, '澶囪鍔╂墜鐢熸垚鐨勬暀妗堣川閲忎笉閿欙紝浣嗗伓灏旀牸寮忛渶瑕佸井璋冦€?, 1, 0, '2026-03-22 10:58:54');
INSERT INTO `t_review` VALUES (3, 'agent', 6, NULL, 3, '鐜嬪紑鍙?, NULL, 5, '浠ｇ爜鍔╂墜寰堝己澶э紝鏀寔澶氱璇█锛岃皟璇曞缓璁篃寰堜笓涓氥€?, 5, 0, '2026-03-22 10:58:54');
INSERT INTO `t_review` VALUES (4, 'agent', 6, NULL, 2, 'dept_admin', '', 4, '1111', 0, 0, '2026-03-24 23:22:15');

-- ----------------------------
-- Table structure for t_review_helpful_rel
-- ----------------------------
DROP TABLE IF EXISTS `t_review_helpful_rel`;
CREATE TABLE `t_review_helpful_rel`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `review_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_review_helpful`(`review_id` ASC, `user_id` ASC) USING BTREE,
  INDEX `fk_helpful_user`(`user_id` ASC) USING BTREE,
  CONSTRAINT `fk_helpful_review` FOREIGN KEY (`review_id`) REFERENCES `t_review` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_helpful_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`user_id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '璇勮鏈夌敤鏍囪鍏宠仈' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of t_review_helpful_rel
-- ----------------------------

-- ----------------------------
-- Table structure for t_sandbox_session
-- ----------------------------
DROP TABLE IF EXISTS `t_sandbox_session`;
CREATE TABLE `t_sandbox_session`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `session_token` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `owner_user_id` bigint NOT NULL,
  `api_key_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `api_key_prefix` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'active',
  `allowed_resource_types` json NULL,
  `max_calls` int NOT NULL DEFAULT 100,
  `used_calls` int NOT NULL DEFAULT 0,
  `max_timeout_sec` int NOT NULL DEFAULT 30,
  `expires_at` datetime NOT NULL,
  `last_invoke_at` datetime NULL DEFAULT NULL,
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_sandbox_token`(`session_token` ASC) USING BTREE,
  INDEX `idx_sandbox_owner_status`(`owner_user_id` ASC, `status` ASC) USING BTREE,
  INDEX `idx_sandbox_expire`(`expires_at` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '娌欑浼氳瘽琛? ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of t_sandbox_session
-- ----------------------------

-- ----------------------------
-- Table structure for t_security_setting
-- ----------------------------
DROP TABLE IF EXISTS `t_security_setting`;
CREATE TABLE `t_security_setting`  (
  `key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `value` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `label` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `description` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `options` json NULL,
  `category` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  PRIMARY KEY (`key`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '瀹夊叏璁剧疆琛? ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of t_security_setting
-- ----------------------------
INSERT INTO `t_security_setting` VALUES ('audit_log_retention', '90', '瀹¤鏃ュ織淇濈暀', '瀹¤鏃ュ織淇濈暀澶╂暟', 'number', NULL, '鏁版嵁瀹夊叏');
INSERT INTO `t_security_setting` VALUES ('data_encryption', 'true', '鏁版嵁鍔犲瘑', '鏄惁鍚敤鏁忔劅鏁版嵁鍔犲瘑瀛樺偍', 'toggle', NULL, '鏁版嵁瀹夊叏');
INSERT INTO `t_security_setting` VALUES ('password_complexity', 'medium', '瀵嗙爜澶嶆潅搴?, '瀵嗙爜澶嶆潅搴﹁姹?, 'select', '[\"low\", \"medium\", \"high\"]', '璁よ瘉');
INSERT INTO `t_security_setting` VALUES ('session_binding', 'none', '浼氳瘽缁戝畾', '浼氳瘽缁戝畾鏂瑰紡', 'select', '[\"none\", \"ip\", \"device\"]', '璁よ瘉');

-- ----------------------------
-- Table structure for t_sensitive_word
-- ----------------------------
DROP TABLE IF EXISTS `t_sensitive_word`;
CREATE TABLE `t_sensitive_word`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `word` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `category` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'default',
  `severity` int NOT NULL DEFAULT 1,
  `source` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'manual',
  `enabled` tinyint(1) NOT NULL DEFAULT 1,
  `created_by` bigint NULL DEFAULT NULL,
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_sensitive_word`(`word` ASC) USING BTREE,
  INDEX `idx_sensitive_enabled`(`enabled` ASC) USING BTREE,
  INDEX `idx_sensitive_category`(`category` ASC) USING BTREE,
  INDEX `idx_sensitive_severity`(`severity` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '鏁忔劅璇嶈〃' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of t_sensitive_word
-- ----------------------------

-- ----------------------------
-- Table structure for t_system_param
-- ----------------------------
DROP TABLE IF EXISTS `t_system_param`;
CREATE TABLE `t_system_param`  (
  `key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `value` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `description` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `category` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `editable` tinyint(1) NULL DEFAULT 1,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`key`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '绯荤粺鍙傛暟琛? ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of t_system_param
-- ----------------------------
INSERT INTO `t_system_param` VALUES ('auto_lock_attempts', '5', 'number', '杩炵画鐧诲綍澶辫触閿佸畾娆℃暟', '瀹夊叏', 1, '2026-03-22 00:32:55');
INSERT INTO `t_system_param` VALUES ('enable_registration', 'true', 'boolean', '鏄惁寮€鏀炬敞鍐?, '绯荤粺', 1, '2026-03-22 00:32:55');
INSERT INTO `t_system_param` VALUES ('max_concurrent_sessions', '5', 'number', '鏈€澶у悓鏃跺湪绾夸細璇濇暟', '鐢ㄦ埛', 1, '2026-03-22 00:32:55');
INSERT INTO `t_system_param` VALUES ('max_upload_size_mb', '50', 'number', '鍗曟枃浠朵笂浼犲ぇ灏忎笂闄愶紙MB锛?, '瀛樺偍', 1, '2026-03-22 00:32:55');
INSERT INTO `t_system_param` VALUES ('password_min_length', '8', 'number', '瀵嗙爜鏈€灏忛暱搴?, '瀹夊叏', 1, '2026-03-22 00:32:55');
INSERT INTO `t_system_param` VALUES ('session_timeout_minutes', '120', 'number', '浼氳瘽瓒呮椂鏃堕棿锛堝垎閽燂級', '瀹夊叏', 1, '2026-03-22 00:32:55');

-- ----------------------------
-- Table structure for t_tag
-- ----------------------------
DROP TABLE IF EXISTS `t_tag`;
CREATE TABLE `t_tag`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `category` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `usage_count` int NULL DEFAULT 0,
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_tag_name_cat`(`name` ASC, `category` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 11 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '鏍囩琛? ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of t_tag
-- ----------------------------
INSERT INTO `t_tag` VALUES (1, '鏁欏姟', 'agent', 15, '2026-03-22 10:58:54');
INSERT INTO `t_tag` VALUES (2, '绉戠爺', 'agent', 12, '2026-03-22 10:58:54');
INSERT INTO `t_tag` VALUES (3, '鍔炲叕', 'skill', 20, '2026-03-22 10:58:54');
INSERT INTO `t_tag` VALUES (4, '鎼滅储', 'agent', 25, '2026-03-22 10:58:54');
INSERT INTO `t_tag` VALUES (5, '鐢熸垚', 'skill', 18, '2026-03-22 10:58:54');
INSERT INTO `t_tag` VALUES (6, '缈昏瘧', 'skill', 8, '2026-03-22 10:58:54');
INSERT INTO `t_tag` VALUES (7, '鏁版嵁', 'dataset', 10, '2026-03-22 10:58:54');
INSERT INTO `t_tag` VALUES (8, '鏍″洯', 'agent', 22, '2026-03-22 10:58:54');
INSERT INTO `t_tag` VALUES (9, 'AI', 'general', 30, '2026-03-22 10:58:54');
INSERT INTO `t_tag` VALUES (10, '鏁堢巼', 'general', 16, '2026-03-22 10:58:54');

-- ----------------------------
-- Table structure for t_trace_span
-- ----------------------------
DROP TABLE IF EXISTS `t_trace_span`;
CREATE TABLE `t_trace_span`  (
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `trace_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `parent_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `operation_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `service_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `start_time` datetime NOT NULL,
  `duration` int NOT NULL,
  `status` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `tags` json NULL,
  `logs` json NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_trace_span_trace`(`trace_id` ASC) USING BTREE,
  INDEX `idx_trace_span_service_time`(`service_name` ASC, `start_time` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '閾捐矾杩借釜琛? ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of t_trace_span
-- ----------------------------

-- ----------------------------
-- Table structure for t_user
-- ----------------------------
DROP TABLE IF EXISTS `t_user`;
CREATE TABLE `t_user`  (
  `user_id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `password_hash` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `real_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `sex` smallint NULL DEFAULT 0,
  `school_id` bigint NOT NULL,
  `menu_id` bigint NULL DEFAULT NULL,
  `major` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `class` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `role` smallint NOT NULL DEFAULT 0,
  `mobile` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `mail` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `head_image` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `zw` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `zc` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `birthday` date NULL DEFAULT NULL,
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'active',
  `last_login_time` datetime NULL DEFAULT NULL,
  `deleted` smallint NULL DEFAULT 0,
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `language` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'zh-CN' COMMENT '璇█鍋忓ソ',
  PRIMARY KEY (`user_id`) USING BTREE,
  UNIQUE INDEX `uk_user_username`(`username` ASC) USING BTREE,
  INDEX `idx_user_school_id`(`school_id` ASC) USING BTREE,
  INDEX `idx_user_menu_id`(`menu_id` ASC) USING BTREE,
  INDEX `idx_user_mobile`(`mobile` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 7 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '鐢ㄦ埛琛? ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of t_user
-- ----------------------------
INSERT INTO `t_user` VALUES (1, 'admin', '$2a$12$e0tp9aC1xUXoDf7XNCID7OQ5vYfQDLyUAWVJ9aWhxVz7mypmfNX0i', 'admin', 1, 1, 3, NULL, NULL, 99, '13800138000', 'admin@lzu.edu.cn', 'blob:http://localhost:3000/b0006b20-3952-4e9c-88bf-c7259daa68a0', NULL, NULL, NULL, 'active', '2026-03-25 16:30:46', 0, '2026-03-22 10:58:54', '2026-03-25 16:29:51', 'zh-CN');
INSERT INTO `t_user` VALUES (2, 'dept_admin', '$2a$12$e0tp9aC1xUXoDf7XNCID7OQ5vYfQDLyUAWVJ9aWhxVz7mypmfNX0i', 'dept_admin', 1, 1, 2, NULL, NULL, 10, '13800138001', 'dept@lzu.edu.cn', NULL, NULL, NULL, NULL, 'active', '2026-03-25 16:48:30', 0, '2026-03-22 10:58:54', '2026-03-25 16:47:45', 'zh-CN');
INSERT INTO `t_user` VALUES (3, 'developer', '$2a$12$e0tp9aC1xUXoDf7XNCID7OQ5vYfQDLyUAWVJ9aWhxVz7mypmfNX0i', 'developer', 1, 1, 2, NULL, NULL, 5, '13800138002', 'dev@lzu.edu.cn', 'blob:http://localhost:3000/2c4fd1ae-cc6b-457b-b651-a30670fd68e0', NULL, NULL, NULL, 'active', '2026-03-25 17:47:15', 0, '2026-03-22 10:58:54', '2026-03-25 16:47:48', 'zh-CN');
INSERT INTO `t_user` VALUES (4, 'testuser', '$2a$12$e0tp9aC1xUXoDf7XNCID7OQ5vYfQDLyUAWVJ9aWhxVz7mypmfNX0i', '璧靛悓瀛?, 0, 1, 2, NULL, NULL, 1, '13800138003', 'student@lzu.edu.cn', NULL, NULL, NULL, NULL, 'active', '2026-03-23 15:38:48', 0, '2026-03-22 10:58:54', '2026-03-22 10:58:54', 'zh-CN');
INSERT INTO `t_user` VALUES (6, 'test002', '$2a$12$zv0RlQBht/9oFR0bshU1kuYuWfciFcqSyIcSMRqa5jpwB7l0lXovK', 'test002', 0, 1, NULL, NULL, NULL, 0, NULL, 'test002@test.com', NULL, NULL, NULL, NULL, 'active', '2026-03-25 16:16:54', 0, '2026-03-22 11:05:20', '2026-03-22 11:05:20', 'zh-CN');

-- ----------------------------
-- Table structure for t_user_role_rel
-- ----------------------------
DROP TABLE IF EXISTS `t_user_role_rel`;
CREATE TABLE `t_user_role_rel`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_role`(`user_id` ASC, `role_id` ASC) USING BTREE,
  INDEX `idx_role_id`(`role_id` ASC) USING BTREE,
  CONSTRAINT `fk_user_role_role` FOREIGN KEY (`role_id`) REFERENCES `t_platform_role` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_user_role_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`user_id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 6 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '鐢ㄦ埛瑙掕壊鍏宠仈琛? ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of t_user_role_rel
-- ----------------------------
INSERT INTO `t_user_role_rel` VALUES (1, 1, 1, '2026-03-24 11:37:43');
INSERT INTO `t_user_role_rel` VALUES (2, 2, 2, '2026-03-24 11:37:43');
INSERT INTO `t_user_role_rel` VALUES (3, 3, 3, '2026-03-24 11:37:43');

SET FOREIGN_KEY_CHECKS = 1;
