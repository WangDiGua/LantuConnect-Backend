package com.lantu.connect.task;

import com.lantu.connect.task.support.TaskDistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时清理审计日志，保留天数来自 t_security_setting.audit_log_retention。
 * <p>
 * 约定：{@code <= 0} 表示不自动清理（永久保留）；{@code 1..3650} 为保留最近 N 天；缺省或非法回退 90。
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
            if (retentionDays == null || retentionDays <= 0) {
                log.info("{} skipped cleanup (retention disabled / forever)", TASK_NAME);
                return;
            }
            int rows = jdbcTemplate.update(
                    "DELETE FROM t_audit_log WHERE create_time < DATE_SUB(NOW(), INTERVAL ? DAY)",
                    retentionDays);
            log.info("{} cleaned audit logs, retentionDays={}, affectedRows={}", TASK_NAME, retentionDays, rows);
        } catch (DataAccessException ex) {
            log.warn("{} failed: {}", TASK_NAME, ex.getMessage());
        } finally {
            taskDistributedLock.unlock(TASK_NAME);
        }
    }

    private Integer loadRetentionDays() {
        Integer days = jdbcTemplate.query(
                        "SELECT CAST(`value` AS SIGNED) AS retention_days FROM t_security_setting WHERE `key` = 'audit_log_retention' LIMIT 1",
                        rs -> rs.next() ? rs.getInt("retention_days") : null);
        if (days == null) {
            return 90;
        }
        if (days <= 0) {
            return 0;
        }
        return Math.min(days, 3650);
    }
}
