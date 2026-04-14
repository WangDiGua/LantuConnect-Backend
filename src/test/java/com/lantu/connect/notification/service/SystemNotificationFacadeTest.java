package com.lantu.connect.notification.service;

import com.lantu.connect.notification.entity.Notification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemNotificationFacadeTest {

    @Mock
    private MultiChannelNotificationService multiChannelNotificationService;
    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldNotifyApplicantAndAuditAudienceWhenOnboardingSubmitted() {
        SystemNotificationFacade facade = new SystemNotificationFacade(multiChannelNotificationService, jdbcTemplate);
        stubRole("platform_admin", 10L, 11L);
        stubRole("admin", 11L, 12L);
        stubRole("reviewer", 13L);
        stubRole("dept_admin", 14L);
        stubRole("department_admin", 15L);
        stubRole("auditor", 16L);

        facade.notifyOnboardingSubmitted(7L, 99L, "Acme", "接入 MCP");

        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);
        verify(multiChannelNotificationService, times(8)).sendAll(cap.capture());

        List<Notification> notifications = cap.getAllValues();
        Set<Long> userIds = notifications.stream().map(Notification::getUserId).collect(Collectors.toSet());
        assertEquals(Set.of(7L, 10L, 11L, 12L, 13L, 14L, 15L, 16L), userIds);

        Notification applicant = notifications.stream()
                .filter(item -> Long.valueOf(7L).equals(item.getUserId()))
                .findFirst()
                .orElseThrow();
        assertEquals(NotificationEventCodes.ONBOARDING_SUBMITTED, applicant.getType());
        assertEquals("developer_application", applicant.getSourceType());
        assertEquals("99", applicant.getSourceId());
        assertEquals("workflow", applicant.getCategory());
        assertEquals("developer_application:99:onboarding", applicant.getAggregateKey());
        assertEquals("/c/developer-onboarding", applicant.getActionUrl());
        assertEquals("查看我的申请", applicant.getActionLabel());

        Notification reviewer = notifications.stream()
                .filter(item -> Long.valueOf(10L).equals(item.getUserId()))
                .findFirst()
                .orElseThrow();
        assertEquals("/c/developer-applications", reviewer.getActionUrl());
        assertEquals("处理入驻", reviewer.getActionLabel());
    }

    @Test
    void shouldNotifySubmitterAndAuditAudienceWhenResourceSubmitted() {
        SystemNotificationFacade facade = new SystemNotificationFacade(multiChannelNotificationService, jdbcTemplate);
        stubRole("platform_admin", 20L);
        stubRole("admin", 21L);
        stubRole("reviewer", 22L);
        stubRole("dept_admin");
        stubRole("department_admin");
        stubRole("auditor");

        facade.notifyResourceSubmitted(7L, 42L, "mcp", "Weather MCP", false);

        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);
        verify(multiChannelNotificationService, times(4)).sendAll(cap.capture());

        List<Notification> notifications = cap.getAllValues();
        Notification submitter = notifications.stream()
                .filter(item -> Long.valueOf(7L).equals(item.getUserId()))
                .findFirst()
                .orElseThrow();
        assertEquals(NotificationEventCodes.RESOURCE_SUBMITTED, submitter.getType());
        assertEquals("mcp", submitter.getSourceType());
        assertEquals("42", submitter.getSourceId());
        assertEquals("resource:42:publication", submitter.getAggregateKey());
        assertEquals("/c/mcp-center/42", submitter.getActionUrl());
        assertEquals("查看资源", submitter.getActionLabel());

        Notification reviewer = notifications.stream()
                .filter(item -> Long.valueOf(20L).equals(item.getUserId()))
                .findFirst()
                .orElseThrow();
        assertEquals("/c/resource-audit", reviewer.getActionUrl());
        assertEquals("处理审核", reviewer.getActionLabel());
    }

    @Test
    void shouldSendWhenPasswordChanged() {
        SystemNotificationFacade facade = new SystemNotificationFacade(multiChannelNotificationService, jdbcTemplate);

        facade.notifyPasswordChanged(23L);

        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);
        verify(multiChannelNotificationService).sendAll(cap.capture());
        Notification n = cap.getValue();
        assertEquals(23L, n.getUserId());
        assertEquals(NotificationEventCodes.PASSWORD_CHANGED, n.getType());
        assertTrue(n.getBody().contains("账号安全"));
        assertEquals("user", n.getSourceType());
        assertEquals("23", n.getSourceId());
        assertEquals("alert", n.getCategory());
        assertNull(n.getAggregateKey());
    }

    @Test
    void shouldSendWhenApiKeyRevoked() {
        SystemNotificationFacade facade = new SystemNotificationFacade(multiChannelNotificationService, jdbcTemplate);

        facade.notifyApiKeyChanged(5L, "k-1", "prod-key", false);

        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);
        verify(multiChannelNotificationService).sendAll(cap.capture());
        Notification n = cap.getValue();
        assertEquals(5L, n.getUserId());
        assertEquals(NotificationEventCodes.API_KEY_REVOKED, n.getType());
        assertEquals("api_key", n.getSourceType());
        assertEquals("k-1", n.getSourceId());
        assertEquals("security", n.getCategory());
        assertEquals("warning", n.getSeverity());
        assertEquals("api_key:k-1:lifecycle", n.getAggregateKey());
        assertEquals(3, n.getCurrentStep());
        assertEquals("revoked", n.getStepKey());
    }

    @Test
    void shouldSendWhenAlertTriggered() {
        SystemNotificationFacade facade = new SystemNotificationFacade(multiChannelNotificationService, jdbcTemplate);
        stubRole("platform_admin", 10L);

        facade.notifyAlertTriggered("CPU", "high", "cpu_usage", "80", "95", "r-1");

        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);
        verify(multiChannelNotificationService).sendAll(cap.capture());
        Notification n = cap.getValue();
        assertEquals(10L, n.getUserId());
        assertEquals(NotificationEventCodes.ALERT_TRIGGERED, n.getType());
        assertTrue(n.getBody().contains("95"));
        assertEquals("alert", n.getSourceType());
        assertNull(n.getSourceId());
        assertEquals("alert", n.getCategory());
        assertEquals("warning", n.getSeverity());
        verify(multiChannelNotificationService, never()).sendAll(
                org.mockito.ArgumentMatchers.argThat(item -> item != null && Long.valueOf(0L).equals(item.getUserId())));
    }

    @Test
    void shouldBroadcastHighRiskSecurityOperationToAdmins() {
        SystemNotificationFacade facade = new SystemNotificationFacade(multiChannelNotificationService, jdbcTemplate);
        stubRole("platform_admin", 1L, 2L);

        facade.notifySystemSecurityOperation(1L, NotificationEventCodes.SECURITY_SETTING_CHANGED, "修改安全配置", "jwt.secret");

        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);
        verify(multiChannelNotificationService).sendAll(cap.capture());
        Notification n = cap.getValue();
        assertEquals(2L, n.getUserId());
        assertEquals(NotificationEventCodes.SECURITY_SETTING_CHANGED, n.getType());
        assertTrue(n.getBody().contains("jwt.secret"));
        assertEquals("system-config", n.getSourceType());
        assertNull(n.getSourceId());
        assertEquals("alert", n.getCategory());
    }

    private void stubRole(String roleCode, Long... userIds) {
        List<Map<String, Object>> rows = java.util.Arrays.stream(userIds)
                .map(id -> Map.<String, Object>of("user_id", id))
                .toList();
        when(jdbcTemplate.queryForList(anyString(), eq(roleCode))).thenReturn(rows);
    }
}
