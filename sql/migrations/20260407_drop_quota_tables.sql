-- 移除平台「配额」与配额页的资源级限流（t_quota_rate_limit）；统一限流仍用 t_rate_limit_rule。
-- 幂等：可重复执行。

DELETE FROM t_notification WHERE source_type = 'quota';

DROP TABLE IF EXISTS t_quota_rate_limit;
DROP TABLE IF EXISTS t_quota;
