package com.lantu.connect.monitoring.service.impl;

import com.lantu.connect.monitoring.dto.AlertRuleCreateRequest;
import com.lantu.connect.monitoring.entity.AlertRule;
import com.lantu.connect.monitoring.mapper.AlertRuleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertRuleServiceImplTest {

    @Mock
    private AlertRuleMapper alertRuleMapper;

    @InjectMocks
    private AlertRuleServiceImpl alertRuleService;

    @Test
    void createPersistsNormalizedOperatorSeverityDuration() {
        when(alertRuleMapper.insert(any())).thenReturn(1);

        AlertRuleCreateRequest req = new AlertRuleCreateRequest();
        req.setName("rule-a");
        req.setMetric("api.latency.p95");
        req.setOperator(">");
        req.setSeverity("critical");
        req.setDuration("10m");
        req.setThreshold(3.0);

        alertRuleService.create(req);

        ArgumentCaptor<AlertRule> cap = ArgumentCaptor.forClass(AlertRule.class);
        verify(alertRuleMapper).insert(cap.capture());
        AlertRule saved = cap.getValue();
        assertThat(saved.getOperator()).isEqualTo("gt");
        assertThat(saved.getSeverity()).isEqualTo("critical");
        assertThat(saved.getDuration()).isEqualTo("10m");
    }

    @Test
    void createUsesDefaultsWhenOmitted() {
        when(alertRuleMapper.insert(any())).thenReturn(1);
        AlertRuleCreateRequest req = new AlertRuleCreateRequest();
        req.setName("rule-b");
        alertRuleService.create(req);
        ArgumentCaptor<AlertRule> cap = ArgumentCaptor.forClass(AlertRule.class);
        verify(alertRuleMapper).insert(cap.capture());
        assertThat(cap.getValue().getOperator()).isEqualTo("gte");
        assertThat(cap.getValue().getSeverity()).isEqualTo("warning");
        assertThat(cap.getValue().getDuration()).isEqualTo("5m");
    }

    @Test
    void createMapsMediumSeverityToWarning() {
        when(alertRuleMapper.insert(any())).thenReturn(1);
        AlertRuleCreateRequest req = new AlertRuleCreateRequest();
        req.setName("rule-c");
        req.setSeverity("medium");
        alertRuleService.create(req);
        ArgumentCaptor<AlertRule> cap = ArgumentCaptor.forClass(AlertRule.class);
        verify(alertRuleMapper).insert(cap.capture());
        assertThat(cap.getValue().getSeverity()).isEqualTo("warning");
    }
}
