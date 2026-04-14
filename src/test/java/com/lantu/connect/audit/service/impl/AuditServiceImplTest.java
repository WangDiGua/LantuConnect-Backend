package com.lantu.connect.audit.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.audit.entity.AuditItem;
import com.lantu.connect.audit.mapper.AuditItemMapper;
import com.lantu.connect.common.security.CasbinAuthorizationService;
import com.lantu.connect.common.util.UserDisplayNameResolver;
import com.lantu.connect.gateway.security.AgentApiKeyService;
import com.lantu.connect.gateway.security.ResourceInvokeGrantService;
import com.lantu.connect.gateway.service.ResourceRegistryService;
import com.lantu.connect.monitoring.service.ResourceHealthService;
import com.lantu.connect.notification.service.SystemNotificationFacade;
import com.lantu.connect.realtime.AuditPendingPushDebouncer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceImplTest {

    @Mock
    private AuditItemMapper auditItemMapper;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private SystemNotificationFacade systemNotificationFacade;
    @Mock
    private UserDisplayNameResolver userDisplayNameResolver;
    @Mock
    private ResourceInvokeGrantService resourceInvokeGrantService;
    @Mock
    private AgentApiKeyService agentApiKeyService;
    @Mock
    private CasbinAuthorizationService casbinAuthorizationService;
    @Mock
    private ResourceRegistryService resourceRegistryService;
    @Mock
    private ResourceHealthService resourceHealthService;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private AuditPendingPushDebouncer auditPendingPushDebouncer;

    @InjectMocks
    private AuditServiceImpl service;

    @Test
    void resolveAuditItemByPathIdShouldPreferAuditRowId() {
        AuditItem auditRow = auditRow(88L, 999L, "pending_review");
        when(auditItemMapper.selectById(88L)).thenReturn(auditRow);

        AuditItem resolved = ReflectionTestUtils.invokeMethod(
                service,
                "resolvePreferredAuditItem",
                88L,
                "mcp",
                "pending_review");

        assertEquals(88L, resolved.getId());
        assertEquals(999L, resolved.getTargetId());
        verify(auditItemMapper, never()).selectOne(any());
    }

    @Test
    void resolveAuditItemByPathIdShouldNotFallbackToTargetIdWhenAuditRowIdExists() {
        AuditItem wrongStatusAuditRow = auditRow(88L, 999L, "testing");
        when(auditItemMapper.selectById(88L)).thenReturn(wrongStatusAuditRow);

        AuditItem resolved = ReflectionTestUtils.invokeMethod(
                service,
                "resolvePreferredAuditItem",
                88L,
                "mcp",
                "pending_review");

        assertNull(resolved);
        verify(auditItemMapper, never()).selectOne(any());
    }

    private static AuditItem auditRow(Long id, Long targetId, String status) {
        AuditItem item = new AuditItem();
        item.setId(id);
        item.setTargetId(targetId);
        item.setTargetType("mcp");
        item.setStatus(status);
        return item;
    }
}
