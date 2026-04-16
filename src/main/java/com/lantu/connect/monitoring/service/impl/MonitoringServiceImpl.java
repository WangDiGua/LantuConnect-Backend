package com.lantu.connect.monitoring.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.PageResults;
import com.lantu.connect.common.util.UserDisplayNameResolver;
import com.lantu.connect.monitoring.dto.KpiMetric;
import com.lantu.connect.monitoring.dto.PageQuery;
import com.lantu.connect.monitoring.dto.PerformanceAnalysisVO;
import com.lantu.connect.monitoring.dto.PerformanceBucketVO;
import com.lantu.connect.monitoring.dto.PerformanceResourceLeaderboardVO;
import com.lantu.connect.monitoring.dto.PerformanceSlowMethodVO;
import com.lantu.connect.monitoring.dto.PerformanceSummaryVO;
import com.lantu.connect.monitoring.dto.QualityHistoryPointVO;
import com.lantu.connect.monitoring.entity.AlertRecord;
import com.lantu.connect.monitoring.entity.CallLog;
import com.lantu.connect.monitoring.entity.TraceSpan;
import com.lantu.connect.monitoring.mapper.AlertRecordMapper;
import com.lantu.connect.monitoring.mapper.CallLogMapper;
import com.lantu.connect.monitoring.mapper.TraceSpanMapper;
import com.lantu.connect.monitoring.service.MonitoringService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.sql.Timestamp;

/**
 * 监控Monitoring服务实现
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Service
@RequiredArgsConstructor
public class MonitoringServiceImpl implements MonitoringService {

    private static final DateTimeFormatter HOURLY_BUCKET_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00:00");
    private static final DateTimeFormatter DAILY_BUCKET_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd 00:00:00");
    private static final DateTimeFormatter LEGACY_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CallLogMapper callLogMapper;
    private final AlertRecordMapper alertRecordMapper;
    private final TraceSpanMapper traceSpanMapper;
    private final UserDisplayNameResolver userDisplayNameResolver;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<KpiMetric> kpis() {
        Long todayCount = callLogMapper.selectTodayCount();
        Double avgLatency = callLogMapper.selectTodayAvgLatencyMs();
        Long successCount = callLogMapper.selectTodaySuccessCount();
        Long yesterdayCount = queryCountSince(LocalDateTime.now().minusDays(1), LocalDateTime.now().minusDays(0));
        Double yesterdayAvgLatency = queryAvgLatencySince(LocalDateTime.now().minusDays(1), LocalDateTime.now().minusDays(0));
        Long yesterdaySuccess = querySuccessSince(LocalDateTime.now().minusDays(1), LocalDateTime.now().minusDays(0));
        List<KpiMetric> list = new ArrayList<>();
        list.add(buildMetric("call_count_today", number(todayCount), "count", number(yesterdayCount)));
        list.add(buildMetric("avg_latency_ms_today", number(avgLatency), "ms", number(yesterdayAvgLatency)));
        list.add(buildMetric("success_count_today", number(successCount), "count", number(yesterdaySuccess)));
        return list;
    }

    @Override
    public List<Map<String, Object>> performance(String resourceType) {
        PerformanceAnalysisVO analysis = performanceAnalysis("24h", resourceType, null);
        return analysis.getBuckets().stream()
                .filter(bucket -> bucket.getRequestCount() > 0)
                .map(MonitoringServiceImpl::toCompatibilityBucketRow)
                .toList();
    }

    @Override
    public PerformanceAnalysisVO performanceAnalysis(String window, String resourceType, Long resourceId) {
        PerformanceWindowSpec spec = PerformanceWindowSpec.from(window);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = spec.windowStart(now);
        List<PerformanceSample> samples = queryPerformanceSamples(start, resourceType, resourceId);
        List<PerformanceSample> filtered = samples.stream()
                .filter(sample -> matchesResourceType(sample.resourceType(), resourceType))
                .filter(sample -> matchesResourceId(sample.resourceId(), resourceId))
                .toList();
        return buildPerformanceAnalysis(spec, now, resourceType, resourceId, filtered);
    }

    @Override
    public List<Map<String, Object>> callSummaryByResource(int windowHours) {
        int h = Math.max(1, Math.min(windowHours <= 0 ? 24 : windowHours, 168));
        List<Map<String, Object>> raw = jdbcTemplate.queryForList(
                "SELECT COALESCE(NULLIF(TRIM(resource_type), ''), 'unknown') AS resource_type, "
                        + "COUNT(*) AS calls, "
                        + "SUM(CASE WHEN status <> 'success' THEN 1 ELSE 0 END) AS errors, "
                        + "ROUND(AVG(latency_ms), 2) AS avg_latency_ms "
                        + "FROM t_call_log WHERE create_time >= DATE_SUB(NOW(), INTERVAL ? HOUR) "
                        + "GROUP BY COALESCE(NULLIF(TRIM(resource_type), ''), 'unknown') "
                        + "ORDER BY calls DESC",
                h);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : raw) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", String.valueOf(row.get("resource_type")).toLowerCase());
            m.put("resource_type", String.valueOf(row.get("resource_type")).toLowerCase());
            m.put("calls", parseLong(row.get("calls")));
            m.put("errors", parseLong(row.get("errors")));
            m.put("avgLatencyMs", parseDouble(row.get("avg_latency_ms")));
            out.add(m);
        }
        return out;
    }

    @Override
    public PageResult<CallLog> callLogs(PageQuery query) {
        Page<CallLog> page = new Page<>(query.getPage(), query.getPageSize());
        LambdaQueryWrapper<CallLog> q = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            q.and(w -> w.like(CallLog::getMethod, kw)
                    .or().like(CallLog::getAgentName, kw)
                    .or().like(CallLog::getTraceId, kw));
        }
        String callStatus = query.getStatus();
        if (StringUtils.hasText(callStatus) && !"all".equalsIgnoreCase(callStatus.trim())) {
            q.eq(CallLog::getStatus, callStatus.trim());
        }
        applyResourceTypeToLambda(q, query.getResourceType());
        if (query.getResourceId() != null && query.getResourceId() > 0) {
            q.eq(CallLog::getAgentId, String.valueOf(query.getResourceId()));
        }
        q.orderByDesc(CallLog::getCreateTime);
        Page<CallLog> result = callLogMapper.selectPage(page, q);
        enrichCallLogUserNames(result.getRecords());
        return PageResults.from(result);
    }

    @Override
    public PageResult<AlertRecord> alerts(PageQuery query) {
        Page<AlertRecord> page = new Page<>(query.getPage(), query.getPageSize());
        LambdaQueryWrapper<AlertRecord> q = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            q.and(w -> w.like(AlertRecord::getMessage, kw).or().like(AlertRecord::getRuleId, kw));
        }
        if (StringUtils.hasText(query.getSeverity())) {
            q.eq(AlertRecord::getSeverity, query.getSeverity().trim());
        }
        String ast = query.getAlertStatus();
        if (!StringUtils.hasText(ast) && StringUtils.hasText(query.getStatus())) {
            String s = query.getStatus().trim();
            if ("firing".equalsIgnoreCase(s) || "resolved".equalsIgnoreCase(s) || "silenced".equalsIgnoreCase(s)) {
                ast = s;
            }
        }
        if (StringUtils.hasText(ast) && !"all".equalsIgnoreCase(ast.trim())) {
            q.eq(AlertRecord::getStatus, ast.trim());
        }
        applyAlertResourceTypeFilter(q, query.getResourceType());
        q.orderByDesc(AlertRecord::getFiredAt);
        Page<AlertRecord> result = alertRecordMapper.selectPage(page, q);
        return PageResults.from(result);
    }

    @Override
    public PageResult<TraceSpan> traces(PageQuery query) {
        Page<TraceSpan> page = new Page<>(query.getPage(), query.getPageSize());
        LambdaQueryWrapper<TraceSpan> q = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            q.and(w -> w.like(TraceSpan::getTraceId, query.getKeyword())
                    .or().like(TraceSpan::getOperationName, query.getKeyword()));
        }
        q.orderByDesc(TraceSpan::getStartTime);
        Page<TraceSpan> result = traceSpanMapper.selectPage(page, q);
        return PageResults.from(result);
    }

    @Override
    public List<QualityHistoryPointVO> qualityHistory(String resourceType, Long resourceId, LocalDateTime from, LocalDateTime to) {
        LocalDateTime start = from == null ? LocalDateTime.now().minusDays(7) : from;
        LocalDateTime end = to == null ? LocalDateTime.now() : to;
        String typeKey = StringUtils.hasText(resourceType) ? resourceType.trim().toLowerCase() : "agent";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT DATE_FORMAT(create_time, '%Y-%m-%d %H:00:00') AS bucket,
                       COUNT(1) AS total_calls,
                       SUM(CASE WHEN status = 'success' THEN 1 ELSE 0 END) AS success_calls,
                       ROUND(AVG(latency_ms), 2) AS avg_latency
                FROM t_call_log
                WHERE agent_id = ?
                  AND (resource_type = ? OR (? = 'agent' AND resource_type IS NULL))
                  AND create_time >= ?
                  AND create_time <= ?
                GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d %H:00:00')
                ORDER BY bucket ASC
                """, String.valueOf(resourceId), typeKey, typeKey, start, end);
        List<QualityHistoryPointVO> result = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        for (Map<String, Object> row : rows) {
            long total = parseLong(row.get("total_calls"));
            long success = parseLong(row.get("success_calls"));
            double avgLatency = parseDouble(row.get("avg_latency"));
            double successRate = total <= 0 ? 1D : ((double) success / (double) total);
            double latencyFactor = Math.max(0D, 1D - (avgLatency / 8000D));
            int qualityScore = (int) Math.round(successRate * 70D + latencyFactor * 30D);
            qualityScore = Math.max(0, Math.min(100, qualityScore));
            result.add(QualityHistoryPointVO.builder()
                    .bucketTime(LocalDateTime.parse(String.valueOf(row.get("bucket")), fmt))
                    .callCount(total)
                    .successRate(successRate)
                    .avgLatencyMs(avgLatency)
                    .qualityScore(qualityScore)
                    .build());
        }
        return result;
    }

    private PerformanceAnalysisVO buildPerformanceAnalysis(PerformanceWindowSpec spec,
                                                           LocalDateTime now,
                                                           String resourceType,
                                                           Long resourceId,
                                                           List<PerformanceSample> samples) {
        Map<String, StatsAccumulator> buckets = new LinkedHashMap<>();
        for (String bucketKey : spec.expectedBuckets(now)) {
            buckets.put(bucketKey, new StatsAccumulator());
        }
        StatsAccumulator summary = new StatsAccumulator();
        Map<ResourceKey, StatsAccumulator> resourceStats = new LinkedHashMap<>();
        Map<String, StatsAccumulator> methodStats = new LinkedHashMap<>();
        for (PerformanceSample sample : samples) {
            String bucketKey = spec.bucketKey(sample.createTime());
            StatsAccumulator bucketStats = buckets.computeIfAbsent(bucketKey, ignored -> new StatsAccumulator());
            bucketStats.add(sample.latencyMs(), sample.status());
            summary.add(sample.latencyMs(), sample.status());
            resourceStats.computeIfAbsent(
                            new ResourceKey(sample.resourceType(), sample.resourceId(), sample.resourceName()),
                            ignored -> new StatsAccumulator())
                    .add(sample.latencyMs(), sample.status());
            methodStats.computeIfAbsent(normalizeMethod(sample.method()), ignored -> new StatsAccumulator())
                    .add(sample.latencyMs(), sample.status());
        }
        List<PerformanceBucketVO> bucketList = buckets.entrySet().stream()
                .map(entry -> entry.getValue().toBucket(entry.getKey()))
                .toList();
        List<PerformanceResourceLeaderboardVO> leaderboard = resourceStats.entrySet().stream()
                .map(entry -> entry.getValue().toResource(entry.getKey()))
                .sorted((left, right) -> {
                    int byRequest = Long.compare(right.getRequestCount(), left.getRequestCount());
                    if (byRequest != 0) return byRequest;
                    int byP99 = Double.compare(right.getP99LatencyMs(), left.getP99LatencyMs());
                    if (byP99 != 0) return byP99;
                    return Double.compare(right.getErrorRate(), left.getErrorRate());
                })
                .toList();
        List<PerformanceSlowMethodVO> slowMethods = methodStats.entrySet().stream()
                .map(entry -> entry.getValue().toMethod(entry.getKey()))
                .sorted((left, right) -> {
                    int byP99 = Double.compare(right.getP99LatencyMs(), left.getP99LatencyMs());
                    if (byP99 != 0) return byP99;
                    return Long.compare(right.getRequestCount(), left.getRequestCount());
                })
                .toList();
        return PerformanceAnalysisVO.builder()
                .window(spec.code)
                .resourceType(normalizeRequestedResourceType(resourceType))
                .resourceId(resourceId)
                .summary(summary.toSummary())
                .buckets(bucketList)
                .resourceLeaderboard(leaderboard)
                .slowMethods(slowMethods)
                .build();
    }

    private List<PerformanceSample> queryPerformanceSamples(LocalDateTime start, String resourceType, Long resourceId) {
        StringBuilder sql = new StringBuilder("""
                SELECT create_time,
                       latency_ms,
                       status,
                       COALESCE(NULLIF(TRIM(resource_type), ''), 'unknown') AS resource_type,
                       agent_id AS resource_id,
                       COALESCE(NULLIF(TRIM(agent_name), ''), CONCAT(COALESCE(NULLIF(TRIM(resource_type), ''), 'unknown'), '#', agent_id)) AS resource_name,
                       method
                FROM t_call_log
                WHERE create_time >= '
                """);
        sql.append(LEGACY_DATE_TIME_FORMATTER.format(start)).append("'");
        appendResourceTypeFilter(sql, resourceType);
        appendResourceIdFilter(sql, resourceId);
        sql.append(" ORDER BY create_time ASC");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString());
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<PerformanceSample> samples = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            LocalDateTime createTime = parseDateTime(row.get("create_time"));
            if (createTime == null) {
                continue;
            }
            samples.add(new PerformanceSample(
                    createTime,
                    normalizeSampleResourceType(row.get("resource_type")),
                    parseLong(String.valueOf(row.get("resource_id"))),
                    firstText(row.get("resource_name"), "unknown"),
                    firstText(row.get("method"), "UNKNOWN"),
                    normalizeStatus(row.get("status")),
                    (int) Math.max(0L, parseLong(row.get("latency_ms")))));
        }
        return samples;
    }

    private static Map<String, Object> toCompatibilityBucketRow(PerformanceBucketVO bucket) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("bucket", bucket.getBucket());
        row.put("timestamp", bucket.getBucket());
        row.put("requestCount", bucket.getRequestCount());
        row.put("requestRate", bucket.getRequestCount());
        row.put("successCount", bucket.getSuccessCount());
        row.put("errorCount", bucket.getErrorCount());
        row.put("timeoutCount", bucket.getTimeoutCount());
        row.put("successRate", bucket.getSuccessRate());
        row.put("errorRate", bucket.getErrorRate());
        row.put("timeoutRate", bucket.getTimeoutRate());
        row.put("avgLatencyMs", bucket.getAvgLatencyMs());
        row.put("p50Latency", bucket.getP50LatencyMs());
        row.put("p95Latency", bucket.getP95LatencyMs());
        row.put("p99Latency", bucket.getP99LatencyMs());
        row.put("latencyP50", bucket.getP50LatencyMs());
        row.put("latencyP95", bucket.getP95LatencyMs());
        row.put("latencyP99", bucket.getP99LatencyMs());
        row.put("throughput", bucket.getThroughput());
        return row;
    }

    private static void appendResourceTypeFilter(StringBuilder sql, String resourceType) {
        if (!StringUtils.hasText(resourceType) || "all".equalsIgnoreCase(resourceType.trim())) {
            return;
        }
        String normalized = resourceType.trim().toLowerCase();
        if ("unknown".equals(normalized)) {
            sql.append(" AND (resource_type IS NULL OR TRIM(resource_type) = '' OR LOWER(TRIM(resource_type)) = 'unknown')");
            return;
        }
        if (!normalized.matches("[a-z_]+")) {
            return;
        }
        sql.append(" AND LOWER(TRIM(resource_type)) = '").append(normalized).append("'");
    }

    private static void appendResourceIdFilter(StringBuilder sql, Long resourceId) {
        if (resourceId == null || resourceId <= 0) {
            return;
        }
        sql.append(" AND agent_id = '").append(resourceId).append("'");
    }

    private static boolean matchesResourceType(String sampleType, String requestedType) {
        if (!StringUtils.hasText(requestedType) || "all".equalsIgnoreCase(requestedType.trim())) {
            return true;
        }
        String normalized = requestedType.trim().toLowerCase();
        if ("unknown".equals(normalized)) {
            return "unknown".equalsIgnoreCase(sampleType);
        }
        return normalized.equalsIgnoreCase(sampleType);
    }

    private static boolean matchesResourceId(Long sampleId, Long requestedId) {
        return requestedId == null || requestedId <= 0 || Objects.equals(sampleId, requestedId);
    }

    private static String normalizeRequestedResourceType(String resourceType) {
        if (!StringUtils.hasText(resourceType) || "all".equalsIgnoreCase(resourceType.trim())) {
            return "all";
        }
        return resourceType.trim().toLowerCase();
    }

    private static String normalizeSampleResourceType(Object value) {
        String text = firstText(value, "unknown").toLowerCase();
        return text.isBlank() ? "unknown" : text;
    }

    private static String normalizeMethod(String method) {
        if (!StringUtils.hasText(method)) {
            return "UNKNOWN";
        }
        return method.trim();
    }

    private static String normalizeStatus(Object value) {
        String text = firstText(value, "error").toLowerCase();
        if ("success".equals(text)) {
            return "success";
        }
        if ("timeout".equals(text)) {
            return "timeout";
        }
        return "error";
    }

    private static String firstText(Object value, String fallback) {
        String text = value == null ? "" : String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    private static LocalDateTime parseDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime ldt) {
            return ldt;
        }
        if (value instanceof Timestamp ts) {
            return ts.toLocalDateTime();
        }
        if (value instanceof java.util.Date date) {
            return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(text, LEGACY_DATE_TIME_FORMATTER);
            } catch (DateTimeParseException ignoredAgain) {
                return null;
            }
        }
    }

    private static double percentile(List<Integer> values, double ratio) {
        if (values == null || values.isEmpty()) {
            return 0D;
        }
        List<Integer> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());
        int rank = (int) Math.ceil(ratio * sorted.size());
        int index = Math.max(0, Math.min(sorted.size() - 1, rank - 1));
        return round(sorted.get(index), 2);
    }

    private static double round(double value, int scale) {
        double factor = Math.pow(10, scale);
        return Math.round(value * factor) / factor;
    }

    private enum PerformanceWindowSpec {
        SIX_HOURS("6h", 6, 1, HOURLY_BUCKET_FORMATTER),
        TWENTY_FOUR_HOURS("24h", 24, 1, HOURLY_BUCKET_FORMATTER),
        SEVEN_DAYS("7d", 7, 24, DAILY_BUCKET_FORMATTER);

        private final String code;
        private final int bucketCount;
        private final int hoursPerBucket;
        private final DateTimeFormatter formatter;

        PerformanceWindowSpec(String code, int bucketCount, int hoursPerBucket, DateTimeFormatter formatter) {
            this.code = code;
            this.bucketCount = bucketCount;
            this.hoursPerBucket = hoursPerBucket;
            this.formatter = formatter;
        }

        private static PerformanceWindowSpec from(String raw) {
            if (!StringUtils.hasText(raw)) {
                return TWENTY_FOUR_HOURS;
            }
            String value = raw.trim().toLowerCase();
            return switch (value) {
                case "6h" -> SIX_HOURS;
                case "7d" -> SEVEN_DAYS;
                default -> TWENTY_FOUR_HOURS;
            };
        }

        private LocalDateTime windowStart(LocalDateTime now) {
            LocalDateTime currentBucket = bucketStart(now);
            return currentBucket.minusHours((long) hoursPerBucket * (bucketCount - 1));
        }

        private List<String> expectedBuckets(LocalDateTime now) {
            LocalDateTime cursor = windowStart(now);
            List<String> buckets = new ArrayList<>(bucketCount);
            for (int i = 0; i < bucketCount; i++) {
                buckets.add(formatter.format(cursor));
                cursor = cursor.plusHours(hoursPerBucket);
            }
            return buckets;
        }

        private String bucketKey(LocalDateTime createTime) {
            return formatter.format(bucketStart(createTime));
        }

        private LocalDateTime bucketStart(LocalDateTime createTime) {
            if (this == SEVEN_DAYS) {
                return createTime.toLocalDate().atStartOfDay();
            }
            return createTime.withMinute(0).withSecond(0).withNano(0);
        }
    }

    private record PerformanceSample(
            LocalDateTime createTime,
            String resourceType,
            Long resourceId,
            String resourceName,
            String method,
            String status,
            int latencyMs
    ) {
    }

    private record ResourceKey(String resourceType, Long resourceId, String resourceName) {
    }

    private static final class StatsAccumulator {
        private long requestCount;
        private long successCount;
        private long errorCount;
        private long timeoutCount;
        private long latencyTotal;
        private final List<Integer> latencies = new ArrayList<>();

        private void add(int latencyMs, String status) {
            requestCount++;
            latencyTotal += Math.max(latencyMs, 0);
            latencies.add(Math.max(latencyMs, 0));
            if ("success".equals(status)) {
                successCount++;
                return;
            }
            errorCount++;
            if ("timeout".equals(status)) {
                timeoutCount++;
            }
        }

        private PerformanceSummaryVO toSummary() {
            return PerformanceSummaryVO.builder()
                    .requestCount(requestCount)
                    .successCount(successCount)
                    .errorCount(errorCount)
                    .timeoutCount(timeoutCount)
                    .successRate(rate(successCount))
                    .errorRate(rate(errorCount))
                    .timeoutRate(rate(timeoutCount))
                    .avgLatencyMs(avgLatency())
                    .p50LatencyMs(percentile(latencies, 0.50))
                    .p95LatencyMs(percentile(latencies, 0.95))
                    .p99LatencyMs(percentile(latencies, 0.99))
                    .build();
        }

        private PerformanceBucketVO toBucket(String bucket) {
            return PerformanceBucketVO.builder()
                    .bucket(bucket)
                    .requestCount(requestCount)
                    .successCount(successCount)
                    .errorCount(errorCount)
                    .timeoutCount(timeoutCount)
                    .successRate(rate(successCount))
                    .errorRate(rate(errorCount))
                    .timeoutRate(rate(timeoutCount))
                    .avgLatencyMs(avgLatency())
                    .p50LatencyMs(percentile(latencies, 0.50))
                    .p95LatencyMs(percentile(latencies, 0.95))
                    .p99LatencyMs(percentile(latencies, 0.99))
                    .throughput(requestCount)
                    .build();
        }

        private PerformanceResourceLeaderboardVO toResource(ResourceKey resource) {
            return PerformanceResourceLeaderboardVO.builder()
                    .resourceType(resource.resourceType())
                    .resourceId(resource.resourceId())
                    .resourceName(resource.resourceName())
                    .requestCount(requestCount)
                    .errorCount(errorCount)
                    .timeoutCount(timeoutCount)
                    .errorRate(rate(errorCount))
                    .timeoutRate(rate(timeoutCount))
                    .avgLatencyMs(avgLatency())
                    .p99LatencyMs(percentile(latencies, 0.99))
                    .lowSample(requestCount < 5)
                    .build();
        }

        private PerformanceSlowMethodVO toMethod(String method) {
            return PerformanceSlowMethodVO.builder()
                    .method(method)
                    .requestCount(requestCount)
                    .errorCount(errorCount)
                    .errorRate(rate(errorCount))
                    .avgLatencyMs(avgLatency())
                    .p95LatencyMs(percentile(latencies, 0.95))
                    .p99LatencyMs(percentile(latencies, 0.99))
                    .build();
        }

        private double avgLatency() {
            return requestCount <= 0 ? 0D : round((double) latencyTotal / (double) requestCount, 2);
        }

        private double rate(long count) {
            return requestCount <= 0 ? 0D : round((double) count / (double) requestCount, 4);
        }
    }

    private static void applyResourceTypeToLambda(LambdaQueryWrapper<CallLog> q, String resourceType) {
        if (!StringUtils.hasText(resourceType) || "all".equalsIgnoreCase(resourceType.trim())) {
            return;
        }
        String rt = resourceType.trim().toLowerCase();
        if ("unknown".equals(rt)) {
            q.and(w -> w.isNull(CallLog::getResourceType)
                    .or().eq(CallLog::getResourceType, "")
                    .or().eq(CallLog::getResourceType, "unknown"));
        } else {
            q.eq(CallLog::getResourceType, rt);
        }
    }

    private static void applyAlertResourceTypeFilter(LambdaQueryWrapper<AlertRecord> q, String resourceType) {
        if (!StringUtils.hasText(resourceType) || "all".equalsIgnoreCase(resourceType.trim())) {
            return;
        }
        String rt = resourceType.trim().toLowerCase();
        if ("unknown".equals(rt)) {
            q.apply("(labels IS NULL "
                    + "OR JSON_EXTRACT(labels, '$.resource_type') IS NULL "
                    + "OR TRIM(IFNULL(JSON_UNQUOTE(JSON_EXTRACT(labels, '$.resource_type')), '')) IN ('', 'unknown'))");
            return;
        }
        q.and(w -> w.apply(
                "(LOWER(JSON_UNQUOTE(JSON_EXTRACT(labels, '$.resource_type'))) = {0} "
                        + "OR LOWER(JSON_UNQUOTE(JSON_EXTRACT(labels, '$.resourceType'))) = {0})",
                rt));
    }

    private void enrichCallLogUserNames(List<CallLog> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> userIds = records.stream()
                .map(CallLog::getUserId)
                .map(MonitoringServiceImpl::parseLong)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, String> names = userDisplayNameResolver.resolveDisplayNames(userIds);
        records.forEach(item -> {
            Long userId = parseLong(item.getUserId());
            if (userId != null) {
                item.setUsername(names.get(userId));
            }
        });
    }

    private static Long parseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static long parseLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private static double parseDouble(Object value) {
        if (value == null) {
            return 0D;
        }
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0D;
        }
    }

    private static String number(Number n) {
        return n == null ? "0" : String.valueOf(n);
    }

    private KpiMetric buildMetric(String name, String value, String unit, String previousValue) {
        double cur = parseDouble(value);
        double prev = parseDouble(previousValue);
        double delta = cur - prev;
        double percent = prev == 0D ? (cur == 0D ? 0D : 100D) : (delta / prev * 100D);
        String type = Math.abs(delta) < 0.0001 ? "flat" : (delta > 0 ? "up" : "down");
        return KpiMetric.builder()
                .name(name)
                .value(value)
                .unit(unit)
                .previousValue(previousValue)
                .changePercent(String.format("%.2f", percent))
                .changeType(type)
                .build();
    }

    private Long queryCountSince(LocalDateTime from, LocalDateTime to) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_call_log WHERE create_time >= ? AND create_time < ?",
                Long.class, from, to);
    }

    private Double queryAvgLatencySince(LocalDateTime from, LocalDateTime to) {
        return jdbcTemplate.queryForObject(
                "SELECT ROUND(AVG(latency_ms), 2) FROM t_call_log WHERE create_time >= ? AND create_time < ?",
                Double.class, from, to);
    }

    private Long querySuccessSince(LocalDateTime from, LocalDateTime to) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_call_log WHERE status = 'success' AND create_time >= ? AND create_time < ?",
                Long.class, from, to);
    }
}
