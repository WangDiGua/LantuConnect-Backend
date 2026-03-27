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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemNotificationFacadeTest {

    @Mock
    private NotificationService notificationService;
    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldBroadcastWhenOnboardingSubmitted() {
        SystemNotificationFacade facade = new SystemNotificationFacade(notificationService, jdbcTemplate);
        when(jdbcTemplate.queryForList(anyString(), eq("platform_admin")))
                .thenReturn(List.of(Map.of("user_id", 10L), Map.of("user_id", 11L)));

        facade.notifyOnboardingSubmitted(7L, 99L, "Acme", "接入 MCP");

        verify(notificationService).broadcast(
                eq(List.of(10L, 11L)),
                eq(NotificationEventCodes.ONBOARDING_SUBMITTED),
                eq("入驻申请待审核"),
                org.mockito.ArgumentMatchers.contains("申请人"),
                eq("developer_application"),
                eq(99L));
    }

    @Test
    void shouldSendWhenPasswordChanged() {
        SystemNotificationFacade facade = new SystemNotificationFacade(notificationService, jdbcTemplate);
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);

        facade.notifyPasswordChanged(23L);

        verify(notificationService).send(captor.capture());
        Notification n = captor.getValue();
        assertEquals(23L, n.getUserId());
        assertEquals(NotificationEventCodes.PASSWORD_CHANGED, n.getType());
        assertEquals("账号密码已修改", n.getTitle());
        assertTrue(n.getBody().contains("账号安全"));
    }

    @Test
    void shouldSendWhenApiKeyRevoked() {
        SystemNotificationFacade facade = new SystemNotificationFacade(notificationService, jdbcTemplate);
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);

        facade.notifyApiKeyChanged(5L, "k-1", "prod-key", false);

        verify(notificationService).send(captor.capture());
        Notification n = captor.getValue();
        assertEquals(NotificationEventCodes.API_KEY_REVOKED, n.getType());
        assertTrue(n.getBody().contains("撤销成功"));
    }

    @Test
    void shouldSendWhenAlertTriggered() {
        SystemNotificationFacade facade = new SystemNotificationFacade(notificationService, jdbcTemplate);
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);

        facade.notifyAlertTriggered("CPU", "high", "cpu_usage", "80", "95", "r-1");

        verify(notificationService).send(captor.capture());
        Notification n = captor.getValue();
        assertEquals(0L, n.getUserId());
        assertEquals(NotificationEventCodes.ALERT_TRIGGERED, n.getType());
        assertTrue(n.getBody().contains("样本值: 95"));
    }

    @Test
    void shouldBroadcastHighRiskSecurityOperationToAdmins() {
        SystemNotificationFacade facade = new SystemNotificationFacade(notificationService, jdbcTemplate);
        when(jdbcTemplate.queryForList(anyString(), eq("platform_admin")))
                .thenReturn(List.of(Map.of("user_id", 1L), Map.of("user_id", 2L)));

        facade.notifySystemSecurityOperation(1L, NotificationEventCodes.SECURITY_SETTING_CHANGED, "修改安全配置", "jwt.secret");

        verify(notificationService).broadcast(
                eq(List.of(2L)),
                eq(NotificationEventCodes.SECURITY_SETTING_CHANGED),
                eq("系统安全配置变更"),
                org.mockito.ArgumentMatchers.contains("jwt.secret"),
                eq("system-config"),
                eq(null));
    }
}
