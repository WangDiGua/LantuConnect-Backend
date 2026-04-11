package com.lantu.connect.notification.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

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

        verify(multiChannelNotificationService).sendAll(
                eq(10L),
                eq(NotificationEventCodes.ONBOARDING_SUBMITTED),
                eq("入驻申请待审核"),
                org.mockito.ArgumentMatchers.contains("申请人"),
                eq("developer_application"),
                eq("99"));
        verify(multiChannelNotificationService).sendAll(
                eq(11L),
                eq(NotificationEventCodes.ONBOARDING_SUBMITTED),
                eq("入驻申请待审核"),
                org.mockito.ArgumentMatchers.contains("申请人"),
                eq("developer_application"),
                eq("99"));
    }

    @Test
    void shouldSendWhenPasswordChanged() {
        SystemNotificationFacade facade = new SystemNotificationFacade(multiChannelNotificationService, jdbcTemplate);

        facade.notifyPasswordChanged(23L);

        verify(multiChannelNotificationService).sendAll(
                eq(23L),
                eq(NotificationEventCodes.PASSWORD_CHANGED),
                eq("账号密码已修改"),
                org.mockito.ArgumentMatchers.contains("账号安全"),
                eq("user"),
                eq("23"));
    }

    @Test
    void shouldSendWhenApiKeyRevoked() {
        SystemNotificationFacade facade = new SystemNotificationFacade(multiChannelNotificationService, jdbcTemplate);

        facade.notifyApiKeyChanged(5L, "k-1", "prod-key", false);

        verify(multiChannelNotificationService).sendAll(
                eq(5L),
                eq(NotificationEventCodes.API_KEY_REVOKED),
                eq("API Key 已撤销"),
                org.mockito.ArgumentMatchers.contains("撤销成功"),
                eq("api_key"),
                eq("k-1"));
    }

    @Test
    void shouldSendWhenAlertTriggered() {
        SystemNotificationFacade facade = new SystemNotificationFacade(multiChannelNotificationService, jdbcTemplate);
        when(jdbcTemplate.queryForList(anyString(), eq("platform_admin")))
                .thenReturn(List.of(Map.of("user_id", 10L)));

        facade.notifyAlertTriggered("CPU", "high", "cpu_usage", "80", "95", "r-1");

        verify(multiChannelNotificationService).sendAll(
                eq(10L),
                eq(NotificationEventCodes.ALERT_TRIGGERED),
                eq("系统告警触发: CPU"),
                org.mockito.ArgumentMatchers.contains("样本值: 95"),
                eq("alert"),
                eq(null));
        verify(multiChannelNotificationService, never()).sendAll(
                eq(0L), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void shouldBroadcastHighRiskSecurityOperationToAdmins() {
        SystemNotificationFacade facade = new SystemNotificationFacade(multiChannelNotificationService, jdbcTemplate);
        when(jdbcTemplate.queryForList(anyString(), eq("platform_admin")))
                .thenReturn(List.of(Map.of("user_id", 1L), Map.of("user_id", 2L)));

        facade.notifySystemSecurityOperation(1L, NotificationEventCodes.SECURITY_SETTING_CHANGED, "修改安全配置", "jwt.secret");

        verify(multiChannelNotificationService).sendAll(
                eq(2L),
                eq(NotificationEventCodes.SECURITY_SETTING_CHANGED),
                eq("系统安全配置变更"),
                org.mockito.ArgumentMatchers.contains("jwt.secret"),
                eq("system-config"),
                eq(null));
    }
}
