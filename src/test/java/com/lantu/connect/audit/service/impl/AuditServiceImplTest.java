package com.lantu.connect.audit.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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

    @BeforeEach
    void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AuditItem.class);
    }

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
        AuditItem wrongStatusAuditRow = auditRow(88L, 999L, "published");
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

    @Test
    void approveResourceShouldPublishImmediatelyWithoutTesting() {
        AuditItem auditRow = auditRow(88L, 999L, "pending_review");
        auditRow.setTargetType("agent");
        auditRow.setDisplayName("Dify Agent");
        auditRow.setSubmitter("7");
        when(auditItemMapper.selectById(88L)).thenReturn(auditRow);
        when(auditItemMapper.update(any(), any())).thenReturn(1);
        doReturn(1).when(jdbcTemplate).update(anyString(), any(), any(), any(), any());
        doReturn(1).when(jdbcTemplate).update(anyString(), any(), any());

        service.approveResource(88L, 11L);

        verify(agentApiKeyService).ensureActiveKeyForAgent(999L, 11L);
        verify(resourceHealthService).ensurePolicyForResource(999L);
        verify(auditPendingPushDebouncer).requestFlush();
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
