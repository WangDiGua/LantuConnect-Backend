package com.lantu.connect.common.diagnostics;

import com.lantu.connect.common.config.LantuConnectLoggingProperties;
import com.lantu.connect.monitoring.dto.ResourceHealthSnapshotVO;
import com.lantu.connect.monitoring.service.ResourceHealthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class StartupDiagnosticsRunner {

    private static final List<String> REQUIRED_TABLES = List.of(
            "t_resource",
            "t_resource_runtime_policy",
            "t_system_config"
    );

    private static final List<String> LEGACY_OBJECTS = List.of(
            "t_resource_agent_ext",
            "t_resource_skill_ext",
            "t_resource_mcp_ext",
            "t_resource_app_ext",
            "t_resource_dataset_ext",
            "t_resource_common_ext",
            "t_system_param",
            "t_security_setting"
    );

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final ResourceHealthService resourceHealthService;
    private final LantuConnectLoggingProperties loggingProperties;

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void runStartupDiagnostics() {
        if (!loggingProperties.isStartupSelfCheckEnabled()) {
            log.info("[startup-check] skipped: disabled by lantu.logging.startup-self-check-enabled=false");
            return;
        }

        StopWatch watch = new StopWatch("startup-diagnostics");
        log.info("");
        log.info("┌────────────────────────────────────────────────────────────┐");
        log.info("│ LantuConnect startup diagnostics                           │");
        log.info("└────────────────────────────────────────────────────────────┘");

        watch.start("database");
        boolean databaseOk = checkDatabase();
        watch.stop();

        watch.start("redis");
        boolean redisOk = checkRedis();
        watch.stop();

        watch.start("schema");
        boolean schemaOk = checkSchema();
        watch.stop();

        watch.start("resources");
        ResourceProbeSummary resourceSummary = checkResources();
        watch.stop();

        long totalMs = watch.getTotalTimeMillis();
        if (!databaseOk || !redisOk || !schemaOk || resourceSummary.failed > 0) {
            log.warn("[startup-check] completed with warnings: database={}, redis={}, schema={}, probed={}, healthy={}, degraded={}, down={}, failed={}, elapsedMs={}",
                    status(databaseOk), status(redisOk), status(schemaOk),
                    resourceSummary.probed, resourceSummary.healthy, resourceSummary.degraded, resourceSummary.down,
                    resourceSummary.failed, totalMs);
        } else if (totalMs >= loggingProperties.getStartupSlowThresholdMs()) {
            log.warn("[startup-check] completed slowly: elapsedMs={}, probed={}, healthy={}, degraded={}, down={}",
                    totalMs, resourceSummary.probed, resourceSummary.healthy, resourceSummary.degraded, resourceSummary.down);
        } else {
            log.info("[startup-check] completed: database={}, redis={}, schema={}, probed={}, healthy={}, degraded={}, down={}, elapsedMs={}",
                    status(databaseOk), status(redisOk), status(schemaOk),
                    resourceSummary.probed, resourceSummary.healthy, resourceSummary.degraded, resourceSummary.down,
                    totalMs);
        }
    }

    private boolean checkDatabase() {
        try {
            Integer ping = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            String database = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
            Integer tableCount = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM information_schema.tables
                    WHERE table_schema = DATABASE()
                      AND table_type = 'BASE TABLE'
                    """, Integer.class);
            log.info("[startup-check] DB        OK database={}, tables={}, ping={}", database, tableCount, ping);
            return Integer.valueOf(1).equals(ping);
        } catch (Exception ex) {
            log.error("[startup-check] DB        FAIL {}", rootMessage(ex), ex);
            return false;
        }
    }

    private boolean checkRedis() {
        RedisConnectionFactory factory = stringRedisTemplate.getConnectionFactory();
        if (factory == null) {
            log.warn("[startup-check] Redis     FAIL connectionFactory=null");
            return false;
        }
        try (RedisConnection connection = factory.getConnection()) {
            String pong = connection.ping();
            Long dbSize = connection.dbSize();
            log.info("[startup-check] Redis     OK ping={}, keys={}", pong, dbSize);
            return "PONG".equalsIgnoreCase(pong);
        } catch (Exception ex) {
            log.warn("[startup-check] Redis     FAIL {}", rootMessage(ex));
            return false;
        }
    }

    private boolean checkSchema() {
        try {
            int requiredCount = countObjects(REQUIRED_TABLES);
            int legacyCount = countObjects(LEGACY_OBJECTS);
            int legacyTriggerCount = countLegacyTriggers();
            int resourceDetailColumns = queryCount("""
                    SELECT COUNT(*)
                    FROM information_schema.columns
                    WHERE table_schema = DATABASE()
                      AND table_name = 't_resource'
                      AND column_name IN ('is_public', 'service_detail_md', 'detail_json')
                    """);
            int badJson = queryCount("SELECT COUNT(*) FROM t_resource WHERE detail_json IS NOT NULL AND JSON_VALID(detail_json) = 0");

            boolean ok = requiredCount == REQUIRED_TABLES.size()
                    && legacyCount == 0
                    && legacyTriggerCount == 0
                    && resourceDetailColumns == 3
                    && badJson == 0;
            if (ok) {
                log.info("[startup-check] Schema    OK requiredTables={}, legacyObjects=0, legacyTriggers=0, resourceColumns=merged",
                        requiredCount);
            } else {
                log.warn("[startup-check] Schema    WARN requiredTables={}/{}, legacyObjects={}, legacyTriggers={}, resourceDetailColumns={}/3, badJson={}",
                        requiredCount, REQUIRED_TABLES.size(), legacyCount, legacyTriggerCount, resourceDetailColumns, badJson);
            }
            return ok;
        } catch (Exception ex) {
            log.error("[startup-check] Schema    FAIL {}", rootMessage(ex), ex);
            return false;
        }
    }

    private ResourceProbeSummary checkResources() {
        ResourceProbeSummary summary = new ResourceProbeSummary();
        try {
            List<Map<String, Object>> byType = jdbcTemplate.queryForList("""
                    SELECT r.resource_type AS resource_type, LOWER(r.status) AS status, COUNT(*) AS count
                    FROM t_resource r
                    WHERE r.deleted = 0
                    GROUP BY r.resource_type, LOWER(r.status)
                    ORDER BY r.resource_type, LOWER(r.status)
                    """);
            log.info("[startup-check] Resources inventory {}", formatRows(byType));

            List<Map<String, Object>> byHealth = jdbcTemplate.queryForList("""
                    SELECT r.resource_type AS resource_type,
                           COALESCE(NULLIF(LOWER(p.health_status), ''), 'unknown') AS health_status,
                           COALESCE(NULLIF(LOWER(p.current_state), ''), 'unknown') AS circuit_state,
                           COUNT(*) AS count
                    FROM t_resource r
                    LEFT JOIN t_resource_runtime_policy p ON p.resource_id = r.id
                    WHERE r.deleted = 0
                      AND r.resource_type IN ('agent', 'skill', 'mcp')
                    GROUP BY r.resource_type,
                             COALESCE(NULLIF(LOWER(p.health_status), ''), 'unknown'),
                             COALESCE(NULLIF(LOWER(p.current_state), ''), 'unknown')
                    ORDER BY r.resource_type, health_status, circuit_state
                    """);
            log.info("[startup-check] Resources health    {}", formatRows(byHealth));

            if (!loggingProperties.isStartupResourceProbeEnabled()) {
                log.info("[startup-check] Resource probe skipped: disabled by lantu.logging.startup-resource-probe-enabled=false");
                return summary;
            }

            int limit = Math.max(0, loggingProperties.getStartupResourceProbeLimit());
            if (limit == 0) {
                log.info("[startup-check] Resource probe skipped: lantu.logging.startup-resource-probe-limit=0");
                return summary;
            }

            List<Long> targets = jdbcTemplate.query("""
                            SELECT r.id
                            FROM t_resource r
                            LEFT JOIN t_resource_runtime_policy p ON p.resource_id = r.id
                            WHERE r.deleted = 0
                              AND r.resource_type IN ('agent', 'skill', 'mcp')
                              AND LOWER(r.status) = 'published'
                              AND LOWER(COALESCE(p.health_status, '')) <> 'disabled'
                            ORDER BY CASE WHEN p.last_probe_at IS NULL THEN 0 ELSE 1 END,
                                     p.last_probe_at ASC,
                                     r.id ASC
                            LIMIT ?
                            """,
                    (rs, i) -> rs.getLong(1), limit);

            if (targets.isEmpty()) {
                log.info("[startup-check] Resource probe no published targets");
                return summary;
            }

            List<String> failedTargets = new ArrayList<>();
            for (Long resourceId : targets) {
                summary.probed++;
                try {
                    ResourceHealthSnapshotVO snapshot = resourceHealthService.probeAndPersist(resourceId);
                    String health = normalize(snapshot == null ? "down" : snapshot.getHealthStatus());
                    if ("healthy".equals(health)) {
                        summary.healthy++;
                    } else if ("degraded".equals(health)) {
                        summary.degraded++;
                    } else {
                        summary.down++;
                    }
                    log.info("[startup-check] Resource  {} id={} type={} name={} health={} circuit={} latencyMs={} reason={}",
                            statusForHealth(health),
                            resourceId,
                            snapshot == null ? "-" : snapshot.getResourceType(),
                            snapshot == null ? "-" : snapshot.getDisplayName(),
                            health,
                            snapshot == null ? "-" : snapshot.getCircuitState(),
                            snapshot == null ? null : snapshot.getProbeLatencyMs(),
                            snapshot == null ? "-" : snapshot.getLastFailureReason());
                } catch (Exception ex) {
                    summary.failed++;
                    failedTargets.add(String.valueOf(resourceId));
                    log.warn("[startup-check] Resource  FAIL id={} {}", resourceId, rootMessage(ex));
                }
            }
            if (!failedTargets.isEmpty()) {
                log.warn("[startup-check] Resource probe failed target ids={}", String.join(",", failedTargets));
            }
            return summary;
        } catch (DataAccessException ex) {
            summary.failed++;
            log.warn("[startup-check] Resources FAIL {}", rootMessage(ex));
            return summary;
        }
    }

    private int countObjects(List<String> names) {
        String placeholders = String.join(",", names.stream().map(it -> "?").toList());
        List<Object> args = new ArrayList<>(names);
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name IN (%s)
                """.formatted(placeholders), Integer.class, args.toArray());
        return count == null ? 0 : count;
    }

    private int countLegacyTriggers() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.triggers
                WHERE trigger_schema = DATABASE()
                  AND (
                    event_object_table IN (
                      't_resource_agent_ext',
                      't_resource_skill_ext',
                      't_resource_mcp_ext',
                      't_resource_app_ext',
                      't_resource_dataset_ext'
                    )
                    OR trigger_name LIKE 'trg_resource_%_ext_%'
                  )
                """, Integer.class);
        return count == null ? 0 : count;
    }

    private int queryCount(String sql) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count == null ? 0 : count;
    }

    private static String formatRows(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return "[]";
        }
        List<String> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            List<String> parts = new ArrayList<>();
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                parts.add(entry.getKey() + "=" + entry.getValue());
            }
            out.add("{" + String.join(", ", parts) + "}");
        }
        return String.join(" ", out);
    }

    private static String status(boolean ok) {
        return ok ? "OK" : "FAIL";
    }

    private static String statusForHealth(String health) {
        return switch (health) {
            case "healthy" -> "OK";
            case "degraded" -> "WARN";
            default -> "DOWN";
        };
    }

    private static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    private static String rootMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        String message = cursor.getMessage();
        return message == null || message.isBlank() ? cursor.getClass().getSimpleName() : message;
    }

    private static final class ResourceProbeSummary {
        private int probed;
        private int healthy;
        private int degraded;
        private int down;
        private int failed;
    }
}
