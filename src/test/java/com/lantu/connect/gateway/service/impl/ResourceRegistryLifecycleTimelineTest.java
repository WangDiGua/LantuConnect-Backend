package com.lantu.connect.gateway.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.auth.mapper.PlatformRoleMapper;
import com.lantu.connect.common.util.SensitiveDataEncryptor;
import com.lantu.connect.common.util.UserDisplayNameResolver;
import com.lantu.connect.gateway.dto.LifecycleTimelineVO;
import com.lantu.connect.gateway.dto.ResourceManageVO;
import com.lantu.connect.gateway.protocol.ProtocolInvokerRegistry;
import com.lantu.connect.monitoring.service.ResourceHealthService;
import com.lantu.connect.notification.service.SystemNotificationFacade;
import com.lantu.connect.realtime.AuditPendingPushDebouncer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class ResourceRegistryLifecycleTimelineTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private PlatformRoleMapper platformRoleMapper;
    @Mock
    private ProtocolInvokerRegistry protocolInvokerRegistry;
    @Mock
    private SensitiveDataEncryptor sensitiveDataEncryptor;
    @Mock
    private UserDisplayNameResolver userDisplayNameResolver;
    @Mock
    private SystemNotificationFacade systemNotificationFacade;
    @Mock
    private AuditPendingPushDebouncer auditPendingPushDebouncer;
    @Mock
    private ResourceHealthService resourceHealthService;

    @InjectMocks
    private ResourceRegistryServiceImpl resourceRegistryService;

    @Test
    void lifecycleTimeline_should_render_submitter_and_reviewer_display_names() {
        ResourceRegistryServiceImpl spy = spy(resourceRegistryService);
        ResourceManageVO detail = ResourceManageVO.builder()
                .id(71L)
                .resourceType("agent")
                .resourceCode("dify-course-agent")
                .displayName("dify取名助手")
                .status("published")
                .createdBy(3L)
                .createdByName("王帝")
                .createTime(LocalDateTime.of(2026, 4, 19, 16, 1, 12))
                .build();
        doReturn(detail).when(spy).getById(99L, 71L);
        when(jdbcTemplate.queryForList(anyString(), anyLong())).thenReturn(List.of(
                Map.of(
                        "submit_time", LocalDateTime.of(2026, 4, 19, 8, 1, 12),
                        "review_time", LocalDateTime.of(2026, 4, 19, 16, 1, 46),
                        "status", "published",
                        "submitter", "3",
                        "reviewer_id", "1",
                        "reject_reason", "",
                        "create_time", LocalDateTime.of(2026, 4, 19, 8, 1, 12))));
        when(userDisplayNameResolver.resolveDisplayNames(argThat(ids ->
                ids != null && ids.size() == 2 && ids.contains(3L) && ids.contains(1L)))).thenReturn(Map.of(
                3L, "王帝",
                1L, "admin"));

        LifecycleTimelineVO timeline = spy.lifecycleTimeline(99L, 71L);

        assertEquals("王帝", timeline.getEvents().stream()
                .filter(event -> "submitted".equals(event.getEventType()))
                .findFirst()
                .orElseThrow()
                .getActor());
        assertEquals("admin", timeline.getEvents().stream()
                .filter(event -> "published".equals(event.getEventType()))
                .findFirst()
                .orElseThrow()
                .getActor());
    }
}
