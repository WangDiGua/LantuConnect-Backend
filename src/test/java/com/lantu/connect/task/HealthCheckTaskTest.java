package com.lantu.connect.task;

import com.lantu.connect.monitoring.ResourceCircuitHealthBridge;
import com.lantu.connect.monitoring.dto.ResourceHealthSnapshotVO;
import com.lantu.connect.monitoring.service.ResourceHealthService;
import com.lantu.connect.realtime.RealtimePushService;
import com.lantu.connect.task.support.TaskDistributedLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HealthCheckTaskTest {

    @Mock
    private TaskDistributedLock taskDistributedLock;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private RealtimePushService realtimePushService;

    @Mock
    private ResourceCircuitHealthBridge resourceCircuitHealthBridge;

    @Mock
    private ResourceHealthService resourceHealthService;

    @Test
    void run_should_include_published_skills_without_existing_runtime_policy() {
        HealthCheckTask task = new HealthCheckTask(
                taskDistributedLock,
                jdbcTemplate,
                realtimePushService,
                resourceCircuitHealthBridge,
                resourceHealthService);

        when(taskDistributedLock.tryLock("HealthCheck")).thenReturn(true);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(List.of(67L, 70L));
        when(resourceHealthService.probeAndPersist(67L)).thenReturn(ResourceHealthSnapshotVO.builder()
                .resourceId(67L)
                .resourceType("skill")
                .healthStatus("healthy")
                .build());
        when(resourceHealthService.probeAndPersist(70L)).thenReturn(ResourceHealthSnapshotVO.builder()
                .resourceId(70L)
                .resourceType("agent")
                .healthStatus("healthy")
                .build());

        task.run();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class));
        assertTrue(sql.getValue().contains("LEFT JOIN t_resource_runtime_policy"));
        assertTrue(sql.getValue().contains("SELECT r.id"));
        verify(resourceHealthService).probeAndPersist(67L);
        verify(resourceHealthService).probeAndPersist(70L);
    }
}
