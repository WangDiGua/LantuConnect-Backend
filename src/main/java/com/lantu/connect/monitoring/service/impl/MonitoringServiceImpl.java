package com.lantu.connect.monitoring.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.PageResults;
import com.lantu.connect.common.util.UserDisplayNameResolver;
import com.lantu.connect.monitoring.dto.KpiMetric;
import com.lantu.connect.monitoring.dto.PageQuery;
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

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 监控Monitoring服务实现
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Service
@RequiredArgsConstructor
public class MonitoringServiceImpl implements MonitoringService {

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
    public List<Map<String, Object>> performance() {
        QueryWrapper<CallLog> qw = new QueryWrapper<>();
        qw.select(
                        "DATE_FORMAT(create_time, '%Y-%m-%d %H:00:00') AS bucket",
                        "ROUND(AVG(latency_ms), 2) AS avgLatencyMs",
                        "COUNT(*) AS requestCount")
                .ge("create_time", LocalDateTime.now().minusHours(24))
                .groupBy("DATE_FORMAT(create_time, '%Y-%m-%d %H:00:00')")
                .orderByAsc("DATE_FORMAT(create_time, '%Y-%m-%d %H:00:00')");
        List<Map<String, Object>> rows = callLogMapper.selectMaps(qw);
        return rows != null ? rows : Collections.emptyList();
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
        } catch (Exception ex) {
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
        } catch (Exception ex) {
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
