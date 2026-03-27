package com.lantu.connect.task;

import com.lantu.connect.task.support.TaskDistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 按 Agent/Skill 实际数量回写 {@code t_provider.agent_count / skill_count}。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProviderCountSyncTask {

    private static final String TASK_NAME = "ProviderCountSyncTask";

    private final TaskDistributedLock taskDistributedLock;
    private final JdbcTemplate jdbcTemplate;

    @Scheduled(cron = "0 30 * * * ?")
    public void run() {
        if (!taskDistributedLock.tryLock(TASK_NAME)) {
            return;
        }
        try {
            int updated = jdbcTemplate.update("""
                    UPDATE t_provider p
                    SET agent_count = (
                            SELECT COUNT(*) FROM t_resource r
                            WHERE r.provider_id = p.id AND r.deleted = 0 AND r.resource_type = 'agent'
                        ),
                        skill_count = (
                            SELECT COUNT(*) FROM t_resource r
                            WHERE r.provider_id = p.id AND r.deleted = 0 AND r.resource_type = 'skill'
                        )
                    """);
            log.info("{} completed, rows affected (best-effort) = {}", TASK_NAME, updated);
        } finally {
            taskDistributedLock.unlock(TASK_NAME);
        }
    }
}
