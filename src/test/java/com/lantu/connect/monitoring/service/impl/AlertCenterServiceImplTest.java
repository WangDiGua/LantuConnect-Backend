package com.lantu.connect.monitoring.service.impl;

import com.lantu.connect.common.util.UserDisplayNameResolver;
import com.lantu.connect.monitoring.dto.AlertResolveRequest;
import com.lantu.connect.monitoring.entity.AlertRecord;
import com.lantu.connect.monitoring.entity.AlertRule;
import com.lantu.connect.monitoring.mapper.AlertRecordActionMapper;
import com.lantu.connect.monitoring.mapper.AlertRecordMapper;
import com.lantu.connect.monitoring.mapper.AlertRuleMapper;
import com.lantu.connect.monitoring.service.AlertMetricSampler;
import com.lantu.connect.notification.mapper.NotificationMapper;
import com.lantu.connect.notification.service.SystemNotificationFacade;
import com.lantu.connect.realtime.RealtimePushService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertCenterServiceImplTest {

    @Mock
    private AlertRuleMapper alertRuleMapper;
    @Mock
    private AlertRecordMapper alertRecordMapper;
    @Mock
    private AlertRecordActionMapper alertRecordActionMapper;
    @Mock
    private NotificationMapper notificationMapper;
    @Mock
    private AlertMetricSampler alertMetricSampler;
    @Mock
    private UserDisplayNameResolver userDisplayNameResolver;
    @Mock
    private SystemNotificationFacade systemNotificationFacade;
    @Mock
    private RealtimePushService realtimePushService;
    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private AlertCenterServiceImpl alertCenterService;

    @Test
    void evaluateEnabledRulesCreatesFiringRecordWhenThresholdBreached() {
        AlertRule rule = new AlertRule();
        rule.setId("rule-1");
        rule.setName("错误率过高");
        rule.setSeverity("critical");
        rule.setMetric("error_rate");
        rule.setOperator("gte");
        rule.setThreshold(BigDecimal.valueOf(5));
        rule.setDuration("5m");
        rule.setEnabled(true);
        when(alertRuleMapper.selectList(any())).thenReturn(List.of(rule));
        when(alertMetricSampler.sample(rule)).thenReturn(AlertMetricSampler.AlertMetricSample.builder()
                .metric("error_rate")
                .sampleValue(BigDecimal.valueOf(8))
                .sampleSource("5m")
                .summary("sample")
                .snapshot(Map.of("sampleValue", 8))
                .labels(Map.of("resource_type", "agent"))
                .build());
        when(alertMetricSampler.evaluate(BigDecimal.valueOf(8), BigDecimal.valueOf(5), "gte")).thenReturn(true);
        when(alertRecordMapper.selectOne(any())).thenReturn(null);
        when(systemNotificationFacade.findRoleUserIds("platform_admin")).thenReturn(List.of());
        when(systemNotificationFacade.findRoleUserIds("admin")).thenReturn(List.of());

        alertCenterService.evaluateEnabledRules();

        ArgumentCaptor<AlertRecord> captor = ArgumentCaptor.forClass(AlertRecord.class);
        verify(alertRecordMapper).insert(captor.capture());
        AlertRecord saved = captor.getValue();
        assertThat(saved.getRuleId()).isEqualTo("rule-1");
        assertThat(saved.getStatus()).isEqualTo("firing");
        assertThat(saved.getLastSampleValue()).isEqualByComparingTo("8");
        verify(realtimePushService).pushAlertFiring(any(), any(), any(), any(), any());
        verify(systemNotificationFacade, never()).notifyAlertTriggered(any(), any(), any(), any(), any(), any());
    }

    @Test
    void resolveUpdatesStatusAndWritesActionHistory() {
        AlertRecord record = new AlertRecord();
        record.setId("rec-1");
        record.setStatus("firing");
        record.setRuleId("rule-1");
        record.setRuleName("错误率过高");
        record.setFiredAt(LocalDateTime.now().minusMinutes(3));
        when(alertRecordMapper.selectById("rec-1")).thenReturn(record);

        AlertResolveRequest request = new AlertResolveRequest();
        request.setNote("人工确认已经恢复");

        alertCenterService.resolve("rec-1", 12L, request);

        ArgumentCaptor<AlertRecord> recordCaptor = ArgumentCaptor.forClass(AlertRecord.class);
        verify(alertRecordMapper).updateById(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getStatus()).isEqualTo("resolved");
        assertThat(recordCaptor.getValue().getResolvedAt()).isNotNull();
        verify(alertRecordActionMapper).insert(any());
    }
}
