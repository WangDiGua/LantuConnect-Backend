package com.lantu.connect.onboarding.service.impl;

import com.lantu.connect.onboarding.dto.DeveloperStatistics;
import com.lantu.connect.onboarding.service.DeveloperStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DeveloperStatisticsServiceImpl implements DeveloperStatisticsService {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public DeveloperStatistics getMyStatistics(Long userId) {
        String uid = String.valueOf(userId);

        Long totalCalls = 0L;
        Long todayCalls = 0L;
        Long totalErrors = 0L;
        Double avgLatency = 0.0;
        try {
            totalCalls = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_call_log WHERE user_id = ?", Long.class, uid);
            todayCalls = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_call_log WHERE user_id = ? AND DATE(create_time) = CURDATE()",
                    Long.class, uid);
            totalErrors = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_call_log WHERE user_id = ? AND status <> 'success'",
                    Long.class, uid);
            avgLatency = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(AVG(latency_ms), 0) FROM t_call_log WHERE user_id = ?",
                    Double.class, uid);
        } catch (Exception ignored) {
        }
        if (totalCalls == null) totalCalls = 0L;
        if (todayCalls == null) todayCalls = 0L;
        if (totalErrors == null) totalErrors = 0L;
        if (avgLatency == null) avgLatency = 0.0;

        double errorRate = totalCalls > 0 ? (double) totalErrors / totalCalls * 100.0 : 0.0;

        List<Map<String, Object>> callsByDay = jdbcTemplate.queryForList(
                "SELECT DATE(create_time) AS day, COUNT(*) AS cnt "
                        + "FROM t_call_log WHERE user_id = ? "
                        + "AND create_time >= DATE_SUB(NOW(), INTERVAL 7 DAY) "
                        + "GROUP BY DATE(create_time) ORDER BY day",
                uid);

        List<Map<String, Object>> topResources = jdbcTemplate.queryForList(
                "SELECT agent_name, agent_id, COUNT(*) AS cnt "
                        + "FROM t_call_log WHERE user_id = ? "
                        + "GROUP BY agent_name, agent_id ORDER BY cnt DESC LIMIT 10",
                uid);

        List<Map<String, Object>> apiKeyUsage = jdbcTemplate.queryForList(
                "SELECT ak.name AS key_name, ak.prefix AS key_prefix, "
                        + "COALESCE(ak.call_count, 0) AS call_count, ak.last_used_at "
                        + "FROM t_api_key ak "
                        + "WHERE ak.owner_id = ? AND ak.owner_type = 'user' AND ak.status = 'active' "
                        + "ORDER BY call_count DESC",
                uid);

        return DeveloperStatistics.builder()
                .totalCalls(totalCalls)
                .todayCalls(todayCalls)
                .errorRate(Math.round(errorRate * 100.0) / 100.0)
                .avgLatencyMs(Math.round(avgLatency * 100.0) / 100.0)
                .callsByDay(callsByDay)
                .topResources(topResources)
                .apiKeyUsage(apiKeyUsage)
                .build();
    }
}
