package com.lantu.connect.notification.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.notification.entity.Notification;
import com.lantu.connect.notification.mapper.NotificationMapper;
import com.lantu.connect.realtime.RealtimePushService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationMapper notificationMapper;

    @Mock
    private RealtimePushService realtimePushService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void sendCreatesFlowCardWhenAggregateKeyIsNew() {
        NotificationServiceImpl service = new NotificationServiceImpl(
                notificationMapper,
                realtimePushService,
                new ObjectMapper(),
                jdbcTemplate);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyLong(), anyString())).thenReturn(List.of());
        when(notificationMapper.insert(any())).thenAnswer(invocation -> {
            Notification row = invocation.getArgument(0);
            row.setId(100L);
            return 1;
        });
        when(notificationMapper.selectCount(any())).thenReturn(1L);

        Notification notification = new Notification();
        notification.setUserId(7L);
        notification.setType("resource_submitted");
        notification.setTitle("资源发布流程");
        notification.setBody("资源已提交审核");
        notification.setSourceType("mcp");
        notification.setSourceId("42");
        notification.setCategory("workflow");
        notification.setSeverity("info");
        notification.setAggregateKey("resource:42:publication");
        notification.setFlowStatus("running");
        notification.setCurrentStep(1);
        notification.setTotalSteps(4);
        notification.setStepKey("submitted");
        notification.setStepTitle("提交审核");
        notification.setStepStatus("done");
        notification.setStepSummary("已进入审核队列");

        service.send(notification);

        ArgumentCaptor<Notification> inserted = ArgumentCaptor.forClass(Notification.class);
        verify(notificationMapper).insert(inserted.capture());
        Notification row = inserted.getValue();
        assertFalse(row.getIsRead());
        assertEquals("workflow", row.getCategory());
        assertEquals("resource:42:publication", row.getAggregateKey());
        assertEquals("running", row.getFlowStatus());
        assertTrue(row.getStepsJson().contains("\"key\":\"submitted\""));
        assertTrue(row.getStepsJson().contains("\"title\":\"提交审核\""));
        verify(realtimePushService).pushNotificationCreated(7L, row, 1L);
    }

    @Test
    void sendUpdatesExistingFlowCardAndAppendsStep() {
        NotificationServiceImpl service = new NotificationServiceImpl(
                notificationMapper,
                realtimePushService,
                new ObjectMapper(),
                jdbcTemplate);
        Notification existing = new Notification();
        existing.setId(100L);
        existing.setUserId(7L);
        existing.setType("resource_submitted");
        existing.setTitle("资源发布流程");
        existing.setBody("资源已提交审核");
        existing.setSourceType("mcp");
        existing.setSourceId("42");
        existing.setCategory("workflow");
        existing.setSeverity("info");
        existing.setAggregateKey("resource:42:publication");
        existing.setFlowStatus("running");
        existing.setCurrentStep(1);
        existing.setTotalSteps(4);
        existing.setIsRead(true);
        existing.setStepsJson("""
                [{"key":"submitted","title":"提交审核","status":"done","summary":"已进入审核队列","time":"2026-04-13T10:00:00"}]
                """);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyLong(), anyString())).thenReturn(List.of(100L));
        when(notificationMapper.selectById(100L)).thenReturn(existing);
        when(notificationMapper.selectCount(any())).thenReturn(1L);

        Notification notification = new Notification();
        notification.setUserId(7L);
        notification.setType("audit_approved");
        notification.setTitle("资源发布流程");
        notification.setBody("资源审核通过，请进入测试灰度");
        notification.setSourceType("mcp");
        notification.setSourceId("42");
        notification.setCategory("workflow");
        notification.setSeverity("success");
        notification.setAggregateKey("resource:42:publication");
        notification.setFlowStatus("running");
        notification.setCurrentStep(2);
        notification.setTotalSteps(4);
        notification.setStepKey("reviewed");
        notification.setStepTitle("审核通过");
        notification.setStepStatus("done");
        notification.setStepSummary("请在资源中心执行发布上线");

        service.send(notification);

        ArgumentCaptor<Notification> updated = ArgumentCaptor.forClass(Notification.class);
        verify(notificationMapper).updateById(updated.capture());
        Notification row = updated.getValue();
        assertEquals(100L, row.getId());
        assertFalse(row.getIsRead());
        assertEquals("audit_approved", row.getType());
        assertEquals("success", row.getSeverity());
        assertEquals(2, row.getCurrentStep());
        assertTrue(row.getStepsJson().contains("\"key\":\"submitted\""));
        assertTrue(row.getStepsJson().contains("\"key\":\"reviewed\""));
        assertTrue(row.getStepsJson().contains("\"title\":\"审核通过\""));
        verify(realtimePushService).pushNotificationCreated(7L, row, 1L);
    }

    @Test
    void sendAggregatedShouldRecoverFromDuplicateKeyRace() {
        NotificationServiceImpl service = new NotificationServiceImpl(
                notificationMapper,
                realtimePushService,
                new ObjectMapper(),
                jdbcTemplate);
        Notification existing = new Notification();
        existing.setId(100L);
        existing.setUserId(7L);
        existing.setAggregateKey("resource:42:publication");
        existing.setType("resource_submitted");
        existing.setTitle("资源发布流程");
        existing.setBody("资源已提交审核");
        existing.setSourceType("mcp");
        existing.setSourceId("42");
        existing.setCategory("workflow");
        existing.setSeverity("info");
        existing.setFlowStatus("running");
        existing.setCurrentStep(1);
        existing.setTotalSteps(4);
        existing.setIsRead(true);
        existing.setStepsJson("[]");

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyLong(), anyString()))
                .thenReturn(List.of(), List.of(100L));
        when(notificationMapper.insert(any())).thenThrow(new DuplicateKeyException("uk_notification_user_aggregate"));
        when(notificationMapper.selectById(100L)).thenReturn(existing);
        when(notificationMapper.selectCount(any())).thenReturn(1L);

        Notification notification = new Notification();
        notification.setUserId(7L);
        notification.setType("audit_approved");
        notification.setTitle("资源发布流程");
        notification.setBody("资源审核通过");
        notification.setSourceType("mcp");
        notification.setSourceId("42");
        notification.setCategory("workflow");
        notification.setSeverity("success");
        notification.setAggregateKey("resource:42:publication");
        notification.setFlowStatus("running");
        notification.setCurrentStep(2);
        notification.setTotalSteps(4);
        notification.setStepKey("reviewed");
        notification.setStepTitle("审核通过");
        notification.setStepStatus("done");
        notification.setStepSummary("请继续发布");

        service.send(notification);

        verify(notificationMapper).updateById(existing);
        verify(realtimePushService).pushNotificationCreated(7L, existing, 1L);
    }
}
