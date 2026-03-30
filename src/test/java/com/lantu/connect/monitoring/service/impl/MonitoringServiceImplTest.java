package com.lantu.connect.monitoring.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lantu.connect.monitoring.dto.PageQuery;
import com.lantu.connect.monitoring.mapper.AlertRecordMapper;
import com.lantu.connect.monitoring.mapper.CallLogMapper;
import com.lantu.connect.monitoring.mapper.TraceSpanMapper;
import com.lantu.connect.common.util.UserDisplayNameResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonitoringServiceImplTest {

    @Mock
    private CallLogMapper callLogMapper;
    @Mock
    private AlertRecordMapper alertRecordMapper;
    @Mock
    private TraceSpanMapper traceSpanMapper;
    @Mock
    private UserDisplayNameResolver userDisplayNameResolver;

    @InjectMocks
    private MonitoringServiceImpl monitoringService;

    @Test
    void callLogsAppliesStatusFilter() {
        when(callLogMapper.selectPage(any(), ArgumentMatchers.argThat(w -> true)))
                .thenReturn(new Page<>(1, 10));
        PageQuery query = new PageQuery();
        query.setStatus("error");
        monitoringService.callLogs(query);
        verify(callLogMapper).selectPage(any(), any());
    }

    @Test
    void callLogsSkipsStatusWhenAll() {
        when(callLogMapper.selectPage(any(), any())).thenReturn(new Page<>(1, 10));
        PageQuery query = new PageQuery();
        query.setStatus("all");
        monitoringService.callLogs(query);
        verify(callLogMapper).selectPage(any(), any());
    }

    @Test
    void alertsAppliesSeverityAndAlertStatus() {
        when(alertRecordMapper.selectPage(any(), any())).thenReturn(new Page<>(1, 10));
        PageQuery query = new PageQuery();
        query.setSeverity("critical");
        query.setAlertStatus("firing");
        monitoringService.alerts(query);
        verify(alertRecordMapper).selectPage(any(), any());
    }

    @Test
    void alertsSkipsAlertStatusWhenAll() {
        when(alertRecordMapper.selectPage(any(), any())).thenReturn(new Page<>(1, 10));
        PageQuery query = new PageQuery();
        query.setAlertStatus("all");
        monitoringService.alerts(query);
        verify(alertRecordMapper).selectPage(any(), any());
    }
}
