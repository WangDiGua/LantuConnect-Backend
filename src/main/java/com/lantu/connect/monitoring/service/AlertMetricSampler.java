package com.lantu.connect.monitoring.service;

import com.lantu.connect.monitoring.dto.AlertRuleMetricOptionVO;
import com.lantu.connect.monitoring.entity.AlertRule;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AlertMetricSampler {

    private final JdbcTemplate jdbcTemplate;

    public List<AlertRuleMetricOptionVO> metricOptions() {
        return List.of(
                AlertRuleMetricOptionVO.builder()
                        .value("http_5xx_rate")
                        .label("5xx 比率")
                        .description("窗口内状态码 >= 500 的调用占比")
                        .unit("%")
                        .build(),
                AlertRuleMetricOptionVO.builder()
                        .value("latency_p99")
                        .label("P99 延迟")
                        .description("窗口内近似高位延迟")
                        .unit("ms")
                        .build(),
                AlertRuleMetricOptionVO.builder()
                        .value("error_rate")
                        .label("错误率")
                        .description("窗口内非 success 调用占比")
                        .unit("%")
                        .build(),
                AlertRuleMetricOptionVO.builder()
                        .value("gateway_invoke_total_1h")
                        .label("调用量")
                        .description("窗口内调用总量")
                        .unit("count")
                        .build(),
                AlertRuleMetricOptionVO.builder()
                        .value("gateway_invoke_errors_1h")
                        .label("失败次数")
                        .description("窗口内失败与超时总数")
                        .unit("count")
                        .build());
    }

    public AlertMetricSample sample(AlertRule rule) {
        String metric = normalizeMetric(rule.getMetric());
        Window window = parseDuration(rule.getDuration());
        SqlFilter sqlFilter = buildFilter(rule, window);
        BigDecimal sampleValue;
        long totalCount;
        long errorCount;
        if ("latency_p99".equals(metric)) {
            Double latency = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(MAX(latency_ms), 0) FROM t_call_log " + sqlFilter.whereClause(),
                    Double.class,
                    sqlFilter.args().toArray());
            totalCount = queryCount(sqlFilter);
            errorCount = queryErrorCount(sqlFilter);
            sampleValue = decimal(latency);
        } else if ("error_rate".equals(metric)) {
            totalCount = queryCount(sqlFilter);
            errorCount = queryErrorCount(sqlFilter);
            sampleValue = percentage(errorCount, totalCount);
        } else if ("http_5xx_rate".equals(metric)) {
            totalCount = queryCount(sqlFilter);
            long http5xx = queryStatusCodeCount(sqlFilter, 500);
            errorCount = http5xx;
            sampleValue = percentage(http5xx, totalCount);
        } else if ("gateway_invoke_errors_1h".equals(metric)) {
            totalCount = queryCount(sqlFilter);
            errorCount = queryErrorCount(sqlFilter);
            sampleValue = BigDecimal.valueOf(errorCount);
        } else {
            totalCount = queryCount(sqlFilter);
            errorCount = queryErrorCount(sqlFilter);
            sampleValue = BigDecimal.valueOf(totalCount);
        }

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("metric", metric);
        snapshot.put("window", window.label());
        snapshot.put("sampleValue", sampleValue);
        snapshot.put("sampleTime", LocalDateTime.now().toString());
        snapshot.put("totalCalls", totalCount);
        snapshot.put("errorCalls", errorCount);
        snapshot.put("scopeType", normalizeScopeType(rule.getScopeType()));
        snapshot.put("scopeResourceType", emptyToNull(rule.getScopeResourceType()));
        snapshot.put("scopeResourceId", rule.getScopeResourceId());
        if (rule.getLabelFilters() != null && !rule.getLabelFilters().isEmpty()) {
            snapshot.put("labelFilters", rule.getLabelFilters());
        }

        Map<String, Object> labels = new LinkedHashMap<>();
        labels.put("metric", metric);
        labels.put("scope_type", normalizeScopeType(rule.getScopeType()));
        if (StringUtils.hasText(rule.getScopeResourceType())) {
            labels.put("resource_type", rule.getScopeResourceType().trim().toLowerCase(Locale.ROOT));
        }
        if (rule.getScopeResourceId() != null) {
            labels.put("resource_id", rule.getScopeResourceId());
        }
        return AlertMetricSample.builder()
                .metric(metric)
                .sampleValue(sampleValue)
                .sampleSource(window.label())
                .summary("metric=%s, window=%s, total=%d, errors=%d".formatted(metric, window.label(), totalCount, errorCount))
                .snapshot(snapshot)
                .labels(labels)
                .build();
    }

    public boolean evaluate(BigDecimal sampleValue, BigDecimal threshold, String operator) {
        if (sampleValue == null) {
            return false;
        }
        BigDecimal normalizedThreshold = threshold == null ? BigDecimal.ZERO : threshold;
        int cmp = sampleValue.compareTo(normalizedThreshold);
        return switch (normalizeOperator(operator)) {
            case "gt" -> cmp > 0;
            case "gte" -> cmp >= 0;
            case "lt" -> cmp < 0;
            case "lte" -> cmp <= 0;
            case "eq" -> cmp == 0;
            default -> cmp >= 0;
        };
    }

    public String normalizeOperator(String operator) {
        if (!StringUtils.hasText(operator)) {
            return "gte";
        }
        return switch (operator.trim().toLowerCase(Locale.ROOT)) {
            case ">", "gt" -> "gt";
            case ">=", "ge", "gte" -> "gte";
            case "<", "lt" -> "lt";
            case "<=", "le", "lte" -> "lte";
            case "=", "eq" -> "eq";
            default -> "gte";
        };
    }

    public String normalizeMetric(String metric) {
        if (!StringUtils.hasText(metric)) {
            return "gateway_invoke_total_1h";
        }
        return metric.trim().toLowerCase(Locale.ROOT);
    }

    public String normalizeScopeType(String scopeType) {
        if (!StringUtils.hasText(scopeType)) {
            return "global";
        }
        String normalized = scopeType.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "global", "resource_type", "resource" -> normalized;
            default -> "global";
        };
    }

    private Window parseDuration(String duration) {
        String raw = StringUtils.hasText(duration) ? duration.trim().toLowerCase(Locale.ROOT) : "5m";
        try {
            if (raw.endsWith("h")) {
                int hours = Math.max(1, Integer.parseInt(raw.substring(0, raw.length() - 1)));
                return new Window(LocalDateTime.now().minusHours(hours), raw);
            }
            if (raw.endsWith("m")) {
                int minutes = Math.max(1, Integer.parseInt(raw.substring(0, raw.length() - 1)));
                return new Window(LocalDateTime.now().minusMinutes(minutes), raw);
            }
        } catch (NumberFormatException ignored) {
            // fall through
        }
        return new Window(LocalDateTime.now().minusMinutes(5), "5m");
    }

    private SqlFilter buildFilter(AlertRule rule, Window window) {
        List<String> clauses = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        clauses.add("create_time >= ?");
        args.add(window.startAt());
        String scopeType = normalizeScopeType(rule.getScopeType());
        if ("resource_type".equals(scopeType) && StringUtils.hasText(rule.getScopeResourceType())) {
            clauses.add("LOWER(resource_type) = ?");
            args.add(rule.getScopeResourceType().trim().toLowerCase(Locale.ROOT));
        }
        if ("resource".equals(scopeType) && StringUtils.hasText(rule.getScopeResourceType()) && rule.getScopeResourceId() != null) {
            clauses.add("LOWER(resource_type) = ?");
            args.add(rule.getScopeResourceType().trim().toLowerCase(Locale.ROOT));
            clauses.add("agent_id = ?");
            args.add(String.valueOf(rule.getScopeResourceId()));
        }
        if (rule.getLabelFilters() != null) {
            for (Map.Entry<String, String> entry : rule.getLabelFilters().entrySet()) {
                if (!StringUtils.hasText(entry.getKey()) || !StringUtils.hasText(entry.getValue())) {
                    continue;
                }
                String key = entry.getKey().trim().toLowerCase(Locale.ROOT);
                String value = entry.getValue().trim();
                switch (key) {
                    case "status" -> {
                        clauses.add("LOWER(status) = ?");
                        args.add(value.toLowerCase(Locale.ROOT));
                    }
                    case "method" -> {
                        clauses.add("LOWER(method) = ?");
                        args.add(value.toLowerCase(Locale.ROOT));
                    }
                    case "resource_type", "resourcetype" -> {
                        clauses.add("LOWER(resource_type) = ?");
                        args.add(value.toLowerCase(Locale.ROOT));
                    }
                    case "resource_id", "resourceid", "agent_id", "agentid" -> {
                        clauses.add("agent_id = ?");
                        args.add(value);
                    }
                    case "agent_name", "agentname" -> {
                        clauses.add("agent_name = ?");
                        args.add(value);
                    }
                    default -> {
                        // Keep unsupported filters for UI display; v1 evaluation only supports call-log fields.
                    }
                }
            }
        }
        return new SqlFilter("WHERE " + String.join(" AND ", clauses), args);
    }

    private long queryCount(SqlFilter filter) {
        Long value = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_call_log " + filter.whereClause(),
                Long.class,
                filter.args().toArray());
        return value == null ? 0L : value;
    }

    private long queryErrorCount(SqlFilter filter) {
        Long value = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_call_log " + filter.whereClause() + " AND status <> 'success'",
                Long.class,
                filter.args().toArray());
        return value == null ? 0L : value;
    }

    private long queryStatusCodeCount(SqlFilter filter, int minStatusCode) {
        List<Object> args = new ArrayList<>(filter.args());
        args.add(minStatusCode);
        Long value = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_call_log " + filter.whereClause() + " AND status_code >= ?",
                Long.class,
                args.toArray());
        return value == null ? 0L : value;
    }

    private static BigDecimal percentage(long numerator, long denominator) {
        if (denominator <= 0L) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal decimal(Double value) {
        return BigDecimal.valueOf(value == null ? 0D : value).setScale(4, RoundingMode.HALF_UP);
    }

    private static String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record Window(LocalDateTime startAt, String label) {
    }

    private record SqlFilter(String whereClause, List<Object> args) {
    }

    @Data
    @Builder
    public static class AlertMetricSample {
        private String metric;
        private BigDecimal sampleValue;
        private String sampleSource;
        private String summary;
        private Map<String, Object> snapshot;
        private Map<String, Object> labels;
    }
}
