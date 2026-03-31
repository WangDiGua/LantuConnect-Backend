-- Flyway 钉扎：启用 spring.flyway.enabled / FLYWAY_ENABLED 后执行。
-- 既有数据库须已包含 sql/lantu_connect.sql 与 sql/migrations 中的变更；baseline-on-migrate 将把基线标为 0，本版本为首个迁移。
-- 后续增量 DDL 请使用 V2__*.sql、V3__*.sql …

SELECT 1;
