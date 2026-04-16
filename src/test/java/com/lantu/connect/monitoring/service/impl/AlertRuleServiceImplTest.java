package com.lantu.connect.monitoring.service.impl;

import com.lantu.connect.monitoring.dto.AlertRuleCreateRequest;
import com.lantu.connect.monitoring.entity.AlertRule;
import com.lantu.connect.monitoring.mapper.AlertRuleMapper;
import com.lantu.connect.monitoring.service.AlertMetricSampler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertRuleServiceImplTest {

    @Mock
    private AlertRuleMapper alertRuleMapper;

    @Mock
    private AlertMetricSampler alertMetricSampler;

    @InjectMocks
    private AlertRuleServiceImpl alertRuleService;

    @BeforeEach
    void setUp() {
        when(alertMetricSampler.normalizeMetric(nullable(String.class))).thenAnswer(invocation -> {
            String raw = invocation.getArgument(0, String.class);
            return raw == null ? "gateway_invoke_total_1h" : raw.trim().toLowerCase(Locale.ROOT);
        });
        when(alertMetricSampler.normalizeOperator(nullable(String.class))).thenAnswer(invocation -> {
            String raw = invocation.getArgument(0, String.class);
            if (raw == null || raw.isBlank()) return "gte";
            return switch (raw.trim()) {
                case ">" -> "gt";
                case ">=" -> "gte";
                case "<" -> "lt";
                case "<=" -> "lte";
                case "=" -> "eq";
                default -> raw.trim().toLowerCase(Locale.ROOT);
            };
        });
        when(alertMetricSampler.normalizeScopeType(nullable(String.class))).thenAnswer(invocation -> {
            String raw = invocation.getArgument(0, String.class);
            if (raw == null || raw.isBlank()) return "global";
            return raw.trim().toLowerCase(Locale.ROOT);
        });
    }

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
        req.setScopeType("resource_type");
        req.setScopeResourceType("mcp");
        req.setLabelFilters(Map.of("status", "error"));

        alertRuleService.create(req);

        ArgumentCaptor<AlertRule> cap = ArgumentCaptor.forClass(AlertRule.class);
        verify(alertRuleMapper).insert(cap.capture());
        AlertRule saved = cap.getValue();
        assertThat(saved.getOperator()).isEqualTo("gt");
        assertThat(saved.getSeverity()).isEqualTo("critical");
        assertThat(saved.getDuration()).isEqualTo("10m");
        assertThat(saved.getNotifyChannels()).isEmpty();
        assertThat(saved.getScopeType()).isEqualTo("resource_type");
        assertThat(saved.getScopeResourceType()).isEqualTo("mcp");
        assertThat(saved.getLabelFilters()).containsEntry("status", "error");
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
        assertThat(cap.getValue().getNotifyChannels()).isEmpty();
        assertThat(cap.getValue().getScopeType()).isEqualTo("global");
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

    @Test
    @SuppressWarnings("deprecation")
    void createIgnoresLegacyNotifyChannelsInRequest() {
        when(alertRuleMapper.insert(any())).thenReturn(1);
        AlertRuleCreateRequest req = new AlertRuleCreateRequest();
        req.setName("rule-d");
        req.setNotifyChannels(List.of("email", "webhook", "ding"));
        alertRuleService.create(req);
        ArgumentCaptor<AlertRule> cap = ArgumentCaptor.forClass(AlertRule.class);
        verify(alertRuleMapper).insert(cap.capture());
        assertThat(cap.getValue().getNotifyChannels()).isEmpty();
    }
}
