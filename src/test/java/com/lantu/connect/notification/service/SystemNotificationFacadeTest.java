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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemNotificationFacadeTest {

    @Mock
    private MultiChannelNotificationService multiChannelNotificationService;
    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldBroadcastWhenOnboardingSubmitted() {
        SystemNotificationFacade facade = new SystemNotificationFacade(multiChannelNotificationService, jdbcTemplate);
        when(jdbcTemplate.queryForList(anyString(), eq("platform_admin")))
                .thenReturn(List.of(Map.of("user_id", 10L), Map.of("user_id", 11L)));

        facade.notifyOnboardingSubmitted(7L, 99L, "Acme", "接入 MCP");

        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);
        verify(multiChannelNotificationService, org.mockito.Mockito.times(2)).sendAll(cap.capture());
        Notification first = cap.getAllValues().get(0);
        assertEquals(10L, first.getUserId());
        assertEquals(NotificationEventCodes.ONBOARDING_SUBMITTED, first.getType());
        assertEquals("入驻申请待审核", first.getTitle());
        assertTrue(first.getBody().contains("申请人"));
        assertEquals("developer_application", first.getSourceType());
        assertEquals("99", first.getSourceId());
        assertEquals("workflow", first.getCategory());
        assertEquals("developer_application:99:onboarding", first.getAggregateKey());
        assertEquals("running", first.getFlowStatus());
        assertEquals(1, first.getCurrentStep());
        assertEquals(2, first.getTotalSteps());
        assertEquals("submitted", first.getStepKey());
        assertEquals("提交入驻申请", first.getStepTitle());
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
        assertEquals("账号密码已修改", n.getTitle());
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
        assertEquals("API Key 已撤销", n.getTitle());
        assertTrue(n.getBody().contains("撤销成功"));
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
        when(jdbcTemplate.queryForList(anyString(), eq("platform_admin")))
                .thenReturn(List.of(Map.of("user_id", 10L)));

        facade.notifyAlertTriggered("CPU", "high", "cpu_usage", "80", "95", "r-1");

        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);
        verify(multiChannelNotificationService).sendAll(cap.capture());
        Notification n = cap.getValue();
        assertEquals(10L, n.getUserId());
        assertEquals(NotificationEventCodes.ALERT_TRIGGERED, n.getType());
        assertEquals("系统告警触发: CPU", n.getTitle());
        assertTrue(n.getBody().contains("样本值: 95"));
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
        when(jdbcTemplate.queryForList(anyString(), eq("platform_admin")))
                .thenReturn(List.of(Map.of("user_id", 1L), Map.of("user_id", 2L)));

        facade.notifySystemSecurityOperation(1L, NotificationEventCodes.SECURITY_SETTING_CHANGED, "修改安全配置", "jwt.secret");

        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);
        verify(multiChannelNotificationService).sendAll(cap.capture());
        Notification n = cap.getValue();
        assertEquals(2L, n.getUserId());
        assertEquals(NotificationEventCodes.SECURITY_SETTING_CHANGED, n.getType());
        assertEquals("系统安全配置变更", n.getTitle());
        assertTrue(n.getBody().contains("jwt.secret"));
        assertEquals("system-config", n.getSourceType());
        assertNull(n.getSourceId());
        assertEquals("alert", n.getCategory());
    }
}
