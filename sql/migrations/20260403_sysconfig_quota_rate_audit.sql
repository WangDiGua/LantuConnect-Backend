-- 配额按五类资源维度；限流规则可选资源作用域；便于监控/审计筛选
ALTER TABLE t_quota
  ADD COLUMN resource_category VARCHAR(16) NOT NULL DEFAULT 'all'
    COMMENT 'all|agent|skill|mcp|app|dataset'
    AFTER target_name;

ALTER TABLE t_rate_limit_rule
  ADD COLUMN resource_scope VARCHAR(16) NULL DEFAULT NULL
    COMMENT 'NULL 或 all 表示任意调用；否则仅匹配该 resource_type 的网关调用'
    AFTER target_value;
