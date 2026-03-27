package com.lantu.connect.task;

import com.lantu.connect.task.support.TaskDistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时清理审计日志，保留天数来自 t_security_setting.audit_log_retention。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditLogRetentionTask {

    private static final String TASK_NAME = "AuditLogRetentionTask";

    private final TaskDistributedLock taskDistributedLock;
    private final JdbcTemplate jdbcTemplate;

    @Scheduled(cron = "0 20 3 * * ?")
    public void run() {
        if (!taskDistributedLock.tryLock(TASK_NAME)) {
            return;
        }
        try {
            Integer retentionDays = loadRetentionDays();
            int rows = jdbcTemplate.update(
                    "DELETE FROM t_audit_log WHERE create_time < DATE_SUB(NOW(), INTERVAL ? DAY)",
                    retentionDays);
            log.info("{} cleaned audit logs, retentionDays={}, affectedRows={}", TASK_NAME, retentionDays, rows);
        } catch (Exception ex) {
            log.warn("{} failed: {}", TASK_NAME, ex.getMessage());
        } finally {
            taskDistributedLock.unlock(TASK_NAME);
        }
    }

    private Integer loadRetentionDays() {
        Integer days = jdbcTemplate.query(
                        "SELECT CAST(`value` AS SIGNED) AS retention_days FROM t_security_setting WHERE `key` = 'audit_log_retention' LIMIT 1",
                        rs -> rs.next() ? rs.getInt("retention_days") : null);
        if (days == null || days < 7) {
            return 90;
        }
        return Math.min(days, 3650);
    }
}
