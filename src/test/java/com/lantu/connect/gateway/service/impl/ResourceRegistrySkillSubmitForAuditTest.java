package com.lantu.connect.gateway.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.auth.mapper.PlatformRoleMapper;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.util.DeptScopeHelper;
import com.lantu.connect.common.util.UserDisplayNameResolver;
import com.lantu.connect.gateway.protocol.ProtocolInvokerRegistry;
import com.lantu.connect.gateway.service.support.ResourceLifecycleStateMachine;
import com.lantu.connect.notification.service.NotificationService;
import com.lantu.connect.realtime.AuditPendingPushDebouncer;
import com.lantu.connect.notification.service.SystemNotificationFacade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class ResourceRegistrySkillSubmitForAuditTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private PlatformRoleMapper platformRoleMapper;
    @Mock
    private ProtocolInvokerRegistry protocolInvokerRegistry;
    @Mock
    private DeptScopeHelper deptScopeHelper;
    @Mock
    private UserDisplayNameResolver userDisplayNameResolver;
    @Mock
    private NotificationService notificationService;
    @Mock
    private SystemNotificationFacade systemNotificationFacade;
    @Mock
    private AuditPendingPushDebouncer auditPendingPushDebouncer;

    @InjectMocks
    private ResourceRegistryServiceImpl resourceRegistryService;

    @Test
    void submitForAuditAllowsSkillWhenArtifactPresentEvenIfPackInvalid() {
        long uid = 1L;
        long rid = 99L;
        when(platformRoleMapper.selectRolesByUserId(uid)).thenReturn(List.of());
        stubResourceQueries(rid, uid, "invalid");
        when(jdbcTemplate.queryForObject(contains("t_audit_item"), eq(Integer.class), any(), any())).thenReturn(0);
        doReturn(1).when(jdbcTemplate).update(anyString(), any(Object[].class));
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of());

        resourceRegistryService.submitForAudit(uid, rid);

        verify(jdbcTemplate, atLeastOnce()).update(
                contains("UPDATE t_resource SET status"),
                eq(ResourceLifecycleStateMachine.STATUS_PENDING_REVIEW),
                eq(rid));
    }

    @Test
    void submitForAuditAllowsSkillWhenPackValid() {
        long uid = 1L;
        long rid = 100L;
        when(platformRoleMapper.selectRolesByUserId(uid)).thenReturn(List.of());
        stubResourceQueries(rid, uid, "valid");
        when(jdbcTemplate.queryForObject(contains("t_audit_item"), eq(Integer.class), any(), any())).thenReturn(0);
        doReturn(1).when(jdbcTemplate).update(anyString(), any(Object[].class));
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of());

        resourceRegistryService.submitForAudit(uid, rid);

        verify(jdbcTemplate, atLeastOnce()).update(
                contains("UPDATE t_resource SET status"),
                eq(ResourceLifecycleStateMachine.STATUS_PENDING_REVIEW),
                eq(rid));
    }

    private void stubResourceQueries(long rid, long uid, String packStatus) {
        when(jdbcTemplate.queryForList(anyString(), anyLong())).thenAnswer(inv -> {
            String sql = inv.getArgument(0);
            long idArg = ((Number) inv.getArgument(1)).longValue();
            if (idArg == rid && sql.contains("t_resource_skill_ext")) {
                return List.of(Map.of("pack_validation_status", packStatus, "artifact_uri", "/uploads/x.zip"));
            }
            if (idArg == rid && sql.contains("FROM t_resource")) {
                return List.of(Map.of(
                        "id", rid,
                        "resource_type", "skill",
                        "resource_code", "demo",
                        "display_name", "Demo",
                        "description", "",
                        "status", "draft",
                        "source_type", "internal",
                        "created_by", uid));
            }
            if (sql.contains("t_user") && sql.contains("menu_id")) {
                return List.of();
            }
            return List.of();
        });
    }
}
