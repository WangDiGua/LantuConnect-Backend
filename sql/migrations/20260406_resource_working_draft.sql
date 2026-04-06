-- 已发布资源「草稿轨」：编辑写入 t_resource_draft，网关仍读 is_current 版本快照直至审核合并
-- MySQL 8+ utf8mb4

CREATE TABLE IF NOT EXISTS t_resource_draft (
  resource_id BIGINT NOT NULL PRIMARY KEY COMMENT 't_resource.id',
  draft_json JSON NOT NULL COMMENT '与版本快照同结构的期望配置',
  audit_tier VARCHAR(16) NOT NULL DEFAULT 'medium' COMMENT 'low | medium | high',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='已发布资源工作副本（未上线）';

-- 审核项扩展：已发布资源的变更提审
ALTER TABLE t_audit_item
  ADD COLUMN audit_kind VARCHAR(32) NOT NULL DEFAULT 'initial'
    COMMENT 'initial=首次上架流程, published_update=已发布资源变更' AFTER source_type;

ALTER TABLE t_audit_item
  ADD COLUMN payload_json JSON NULL COMMENT 'published_update 冻结快照' AFTER audit_kind;
