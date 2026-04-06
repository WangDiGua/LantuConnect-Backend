package com.lantu.connect.task;

import com.lantu.connect.monitoring.entity.AlertRecord;
import com.lantu.connect.monitoring.entity.AlertRule;
import com.lantu.connect.monitoring.mapper.AlertRecordMapper;
import com.lantu.connect.monitoring.mapper.AlertRuleMapper;
import com.lantu.connect.notification.service.SystemNotificationFacade;
import com.lantu.connect.realtime.RealtimePushService;
import com.lantu.connect.task.support.TaskDistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 告警规则执行任务：按规则评估样本值并落库告警记录。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AlertRuleEvaluateTask {

    private static final String TASK_NAME = "AlertRuleEvaluateTask";

    private final TaskDistributedLock taskDistributedLock;
    private final AlertRuleMapper alertRuleMapper;
    private final AlertRecordMapper alertRecordMapper;
    private final SystemNotificationFacade systemNotificationFacade;
    private final JdbcTemplate jdbcTemplate;
    private final RealtimePushService realtimePushService;

    @Scheduled(cron = "0 */1 * * * ?")
    public void run() {
        if (!taskDistributedLock.tryLock(TASK_NAME)) {
            return;
        }
        try {
            for (AlertRule rule : alertRuleMapper.selectList(null)) {
                if (!Boolean.TRUE.equals(rule.getEnabled())) {
                    continue;
                }
                BigDecimal sample = sampleMetric(rule.getMetric());
                if (sample == null) {
                    continue;
                }
                if (!evaluate(sample, rule.getThreshold(), rule.getOperator())) {
                    continue;
                }
                if (hasActiveFiring(rule.getId())) {
                    continue;
                }
                AlertRecord record = new AlertRecord();
                record.setId(UUID.randomUUID().toString());
                record.setRuleId(rule.getId());
                record.setRuleName(rule.getName());
                record.setSeverity(rule.getSeverity());
                record.setStatus("firing");
                record.setMessage("规则触发: " + rule.getName() + ", sample=" + sample + ", threshold=" + rule.getThreshold());
                record.setSource("alert-evaluator");
                record.setLabels(Map.of("metric", rule.getMetric(), "operator", rule.getOperator()));
                record.setFiredAt(LocalDateTime.now());
                alertRecordMapper.insert(record);

                realtimePushService.pushAlertFiring(
                        rule.getId(),
                        rule.getName(),
                        rule.getSeverity(),
                        record.getId(),
                        record.getMessage());

                systemNotificationFacade.notifyAlertTriggered(
                        rule.getName(),
                        rule.getSeverity(),
                        rule.getMetric(),
                        rule.getThreshold() == null ? null : String.valueOf(rule.getThreshold()),
                        String.valueOf(sample),
                        record.getId());
            }
        } catch (RuntimeException ex) {
            log.warn("{} failed: {}", TASK_NAME, ex.getMessage());
        } finally {
            taskDistributedLock.unlock(TASK_NAME);
        }
    }

    private boolean hasActiveFiring(String ruleId) {
        Long count = alertRecordMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AlertRecord>()
                .eq(AlertRecord::getRuleId, ruleId)
                .eq(AlertRecord::getStatus, "firing"));
        return count != null && count > 0;
    }

    private BigDecimal sampleMetric(String metric) {
        if (metric == null) {
            return null;
        }
        String m = metric.trim().toLowerCase();
        if (m.contains("latency")) {
            Double avg = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(AVG(latency_ms),0) FROM t_call_log WHERE create_time >= DATE_SUB(NOW(), INTERVAL 5 MINUTE)",
                    Double.class);
            return BigDecimal.valueOf(avg == null ? 0D : avg);
        }
        if (m.contains("error_rate")) {
            Long total = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_call_log WHERE create_time >= DATE_SUB(NOW(), INTERVAL 5 MINUTE)",
                    Long.class);
            if (total == null || total == 0L) {
                return BigDecimal.ZERO;
            }
            Long err = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_call_log WHERE create_time >= DATE_SUB(NOW(), INTERVAL 5 MINUTE) AND status <> 'success'",
                    Long.class);
            double rate = (err == null ? 0D : err.doubleValue()) * 100D / total.doubleValue();
            return BigDecimal.valueOf(rate);
        }
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_call_log WHERE create_time >= DATE_SUB(NOW(), INTERVAL 5 MINUTE)",
                Long.class);
        return BigDecimal.valueOf(count == null ? 0L : count);
    }

    private boolean evaluate(BigDecimal value, BigDecimal threshold, String operator) {
        if (threshold == null) {
            threshold = BigDecimal.ZERO;
        }
        String op = operator == null ? ">=" : operator.trim().toLowerCase();
        int cmp = value.compareTo(threshold);
        return switch (op) {
            case ">", "gt" -> cmp > 0;
            case ">=", "gte", "ge" -> cmp >= 0;
            case "<", "lt" -> cmp < 0;
            case "<=", "lte", "le" -> cmp <= 0;
            case "=", "eq" -> cmp == 0;
            default -> cmp >= 0;
        };
    }
}
