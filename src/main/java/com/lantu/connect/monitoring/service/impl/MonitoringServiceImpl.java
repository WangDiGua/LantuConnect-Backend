package com.lantu.connect.monitoring.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.PageResults;
import com.lantu.connect.common.util.UserDisplayNameResolver;
import com.lantu.connect.monitoring.dto.KpiMetric;
import com.lantu.connect.monitoring.dto.PageQuery;
import com.lantu.connect.monitoring.entity.AlertRecord;
import com.lantu.connect.monitoring.entity.CallLog;
import com.lantu.connect.monitoring.entity.TraceSpan;
import com.lantu.connect.monitoring.mapper.AlertRecordMapper;
import com.lantu.connect.monitoring.mapper.CallLogMapper;
import com.lantu.connect.monitoring.mapper.TraceSpanMapper;
import com.lantu.connect.monitoring.service.MonitoringService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import java.time.LocalDateTime;
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

    @Override
    public List<KpiMetric> kpis() {
        Long todayCount = callLogMapper.selectTodayCount();
        Double avgLatency = callLogMapper.selectTodayAvgLatencyMs();
        Long successCount = callLogMapper.selectTodaySuccessCount();
        List<KpiMetric> list = new ArrayList<>();
        list.add(KpiMetric.builder().name("call_count_today").value(String.valueOf(todayCount != null ? todayCount : 0)).unit("count").build());
        list.add(KpiMetric.builder().name("avg_latency_ms_today").value(String.valueOf(avgLatency != null ? avgLatency : 0)).unit("ms").build());
        list.add(KpiMetric.builder().name("success_count_today").value(String.valueOf(successCount != null ? successCount : 0)).unit("count").build());
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
            q.and(w -> w.like(AlertRecord::getMessage, query.getKeyword()).or().like(AlertRecord::getRuleId, query.getKeyword()));
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
}
