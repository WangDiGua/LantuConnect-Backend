package com.lantu.connect.gateway.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.auth.mapper.PlatformRoleMapper;
import com.lantu.connect.common.util.DeptScopeHelper;
import com.lantu.connect.common.util.UserDisplayNameResolver;
import com.lantu.connect.gateway.dto.ResourceManageVO;
import com.lantu.connect.gateway.dto.ResourceUpsertRequest;
import com.lantu.connect.gateway.protocol.ProtocolInvokerRegistry;
import com.lantu.connect.monitoring.service.ResourceHealthService;
import com.lantu.connect.notification.service.NotificationService;
import com.lantu.connect.notification.service.SystemNotificationFacade;
import com.lantu.connect.realtime.AuditPendingPushDebouncer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class ResourceRegistrySkillSnapshotCompatibilityTest {

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
    @Mock
    private ResourceHealthService resourceHealthService;

    @InjectMocks
    private ResourceRegistryServiceImpl resourceRegistryService;

    @BeforeEach
    void setUpObjectMapper() {
        when(objectMapper.convertValue(any(), eq(Map.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void shouldKeepLegacySkillSnapshotKeysReadable() {
        ResourceManageVO current = ResourceManageVO.builder()
                .resourceType("skill")
                .resourceCode("ctx-skill")
                .displayName("Context Skill")
                .skillType("context_v1")
                .executionMode("context")
                .contextPrompt("current prompt")
                .build();

        Map<String, Object> legacySnapshot = new LinkedHashMap<>();
        legacySnapshot.put("packFormat", "context_v1");
        legacySnapshot.put("hostedSystemPrompt", "legacy prompt");
        legacySnapshot.put("executionMode", "context");
        legacySnapshot.put("parametersSchema", Map.of("type", "object"));

        ResourceUpsertRequest merged = ReflectionTestUtils.invokeMethod(
                resourceRegistryService, "mergeSnapshotOntoCurrent", current, legacySnapshot);

        assertNotNull(merged);
        assertEquals("context_v1", merged.getSkillType());
        assertEquals("context", merged.getExecutionMode());
        assertEquals("legacy prompt", merged.getContextPrompt());
    }

    @Test
    void shouldNotWritePackFormatIntoNewSkillVersionSnapshot() {
        long resourceId = 99L;
        stubSkillSnapshotQueries(resourceId);

        @SuppressWarnings("unchecked")
        Map<String, Object> snapshot = ReflectionTestUtils.invokeMethod(
                resourceRegistryService, "snapshotForVersion", "skill", resourceId, new ResourceUpsertRequest(), false);

        assertNotNull(snapshot);
        assertEquals("portal_context", snapshot.get("invokeType"));
        assertEquals("context", snapshot.get("executionMode"));
        assertEquals("legacy prompt", snapshot.get("contextPrompt"));
        assertFalse(snapshot.containsKey("packFormat"));
    }

    private void stubSkillSnapshotQueries(long resourceId) {
        when(jdbcTemplate.queryForList(anyString(), anyLong())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            long idArg = ((Number) invocation.getArgument(1)).longValue();
            if (idArg == resourceId && sql.contains("FROM t_resource") && !sql.contains("t_resource_skill_ext")) {
                return List.of(Map.of(
                        "resource_code", "ctx-skill",
                        "display_name", "Context Skill",
                        "description", "desc",
                        "status", "draft",
                        "access_policy", "open_platform"));
            }
            if (idArg == resourceId && sql.contains("t_resource_skill_ext")) {
                return List.of(Map.of(
                        "skill_type", "context_v1",
                        "execution_mode", "context",
                        "manifest_json", Map.of(),
                        "entry_doc", "",
                        "spec_json", Map.of(),
                        "service_detail_md", "detail",
                        "hosted_system_prompt", "legacy prompt",
                        "parameters_schema", Map.of("type", "object")));
            }
            return List.of();
        });
    }
}
