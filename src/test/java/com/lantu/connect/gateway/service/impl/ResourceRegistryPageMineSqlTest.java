package com.lantu.connect.gateway.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.auth.mapper.PlatformRoleMapper;
import com.lantu.connect.common.util.SensitiveDataEncryptor;
import com.lantu.connect.common.util.UserDisplayNameResolver;
import com.lantu.connect.compat.robotfactory.service.RobotFactoryLifecycleHookService;
import com.lantu.connect.gateway.protocol.ProtocolInvokerRegistry;
import com.lantu.connect.monitoring.service.ResourceHealthService;
import com.lantu.connect.notification.service.SystemNotificationFacade;
import com.lantu.connect.realtime.AuditPendingPushDebouncer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ResourceRegistryPageMineSqlTest {

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
    @Mock
    private RobotFactoryLifecycleHookService robotFactoryLifecycleHookService;

    @InjectMocks
    private ResourceRegistryServiceImpl resourceRegistryService;

    @Test
    void pageMineAppFilterShouldNotDuplicateUnifiedAgentExclusionClause() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        resourceRegistryService.pageMine(1L, "app", null, null, null, "desc", 1, 20, null);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(sqlCaptor.capture(), eq(Long.class), any(Object[].class));
        String countSql = sqlCaptor.getValue();

        assertFalse(countSql.contains("t_resource_detail"), countSql);
        String marker = "JSON_EXTRACT(detail_json, '$.agent_exposure')";
        int occurrences = countSql.split(java.util.regex.Pattern.quote(marker), -1).length - 1;
        assertEquals(1, occurrences, countSql);
        assertFalse(countSql.contains(") )  AND resource_type = 'app'"), countSql);
        assertFalse(countSql.contains("') )"), countSql);
    }
}
