package com.lantu.connect.task;

import com.lantu.connect.task.support.TaskDistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 同步 {@code t_platform_role.user_count}。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserRoleCountSyncTask {

    private static final String TASK_NAME = "UserRoleCountSyncTask";

    private final TaskDistributedLock taskDistributedLock;
    private final JdbcTemplate jdbcTemplate;

    @Scheduled(cron = "0 30 * * * ?")
    public void run() {
        if (!taskDistributedLock.tryLock(TASK_NAME)) {
            return;
        }
        try {
            int updated = jdbcTemplate.update("""
                    UPDATE t_platform_role r
                    SET user_count = (SELECT COUNT(*) FROM t_user_role_rel ur WHERE ur.role_id = r.id)
                    """);
            log.info("{} completed, rows affected (best-effort) = {}", TASK_NAME, updated);
        } finally {
            taskDistributedLock.unlock(TASK_NAME);
        }
    }
}
