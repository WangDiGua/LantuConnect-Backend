package com.lantu.connect.gateway.capability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.gateway.capability.dto.CapabilityCreateRequest;
import com.lantu.connect.gateway.capability.dto.CapabilityInvokeRequest;
import com.lantu.connect.gateway.capability.dto.CapabilityInvokeResultVO;
import com.lantu.connect.gateway.capability.dto.CapabilityToolSessionRequest;
import com.lantu.connect.gateway.capability.dto.CapabilityToolSessionVO;
import com.lantu.connect.gateway.dto.AggregatedCapabilityToolsVO;
import com.lantu.connect.gateway.dto.InvokeRequest;
import com.lantu.connect.gateway.dto.InvokeResponse;
import com.lantu.connect.gateway.dto.ResourceManageVO;
import com.lantu.connect.gateway.dto.ResourceResolveVO;
import com.lantu.connect.gateway.dto.ResourceUpsertRequest;
import com.lantu.connect.gateway.dto.ToolDispatchRouteVO;
import com.lantu.connect.gateway.security.ApiKeyScopeService;
import com.lantu.connect.gateway.service.ResourceRegistryService;
import com.lantu.connect.gateway.service.UnifiedGatewayService;
import com.lantu.connect.monitoring.trace.TraceRecorder;
import com.lantu.connect.usermgmt.entity.ApiKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CapabilityV2ServiceImplTest {

    private JdbcTemplate jdbcTemplate;
    private UnifiedGatewayService unifiedGatewayService;
    private ResourceRegistryService resourceRegistryService;
    private ApiKeyScopeService apiKeyScopeService;
    private TraceRecorder traceRecorder;
    private CapabilityV2ServiceImpl service;
    private ApiKey apiKey;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        unifiedGatewayService = mock(UnifiedGatewayService.class);
        resourceRegistryService = mock(ResourceRegistryService.class);
        apiKeyScopeService = mock(ApiKeyScopeService.class);
        traceRecorder = mock(TraceRecorder.class);
        service = new CapabilityV2ServiceImpl(
                jdbcTemplate,
                new ObjectMapper(),
                new CapabilityImportDetector(),
                unifiedGatewayService,
                resourceRegistryService,
                apiKeyScopeService,
                traceRecorder);
        TraceRecorder.TraceSpanScope spanScope = mock(TraceRecorder.TraceSpanScope.class, RETURNS_SELF);
        when(traceRecorder.normalizeTraceId(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(traceRecorder.openSpan(anyString(), anyString(), anyString(), anyMap())).thenReturn(spanScope);
        apiKey = new ApiKey();
        apiKey.setId("k-1");
    }

    @Test
    void shouldReturnPromptBundleWhenInvokingContextSkillWithoutDelegate() {
        mockCapabilityType(9L, "skill");
        when(unifiedGatewayService.getByTypeAndId("skill", "9", "closure", apiKey, 11L))
                .thenReturn(ResourceResolveVO.builder()
                        .resourceType("skill")
                        .resourceId("9")
                        .displayName("PPT 生成技能")
                        .resourceCode("ppt-skill")
                        .status("published")
                        .spec(Map.of(
                                "contextPrompt", "请输出 PPT 页面结构",
                                "parametersSchema", Map.of("type", "object")))
                        .build());

        CapabilityInvokeRequest request = new CapabilityInvokeRequest();
        request.setPayload(Map.of("topic", "季度经营复盘"));

        CapabilityInvokeResultVO result = service.invoke(9L, 11L, "trace-1", "127.0.0.1", request, apiKey);

        assertNotNull(result.getResponse());
        assertEquals("skill", result.getResponse().getResourceType());
        assertTrue(result.getResponse().getBody().contains("contextPrompt"));
        assertTrue(result.getResponse().getBody().contains("季度经营复盘"));
    }

    @Test
    void shouldDelegateSkillInvokeWhenSpecDeclaresAgentDelegate() {
        mockCapabilityType(9L, "skill");
        when(unifiedGatewayService.getByTypeAndId("skill", "9", "closure", apiKey, 11L))
                .thenReturn(ResourceResolveVO.builder()
                        .resourceType("skill")
                        .resourceId("9")
                        .displayName("PPT 生成技能")
                        .resourceCode("ppt-skill")
                        .status("published")
                        .spec(Map.of(
                                "contextPrompt", "请输出 PPT 页面结构",
                                "runtimeMode", "delegate_agent",
                                "delegate", Map.of("resourceType", "agent", "resourceId", "18")))
                        .build());
        when(unifiedGatewayService.invoke(eq(11L), eq("trace-2"), eq("127.0.0.1"), any(InvokeRequest.class), eq(apiKey)))
                .thenReturn(InvokeResponse.builder()
                        .requestId("req-1")
                        .traceId("trace-2")
                        .resourceType("agent")
                        .resourceId("18")
                        .statusCode(200)
                        .status("success")
                        .latencyMs(12L)
                        .body("{\"answer\":\"ok\"}")
                        .build());

        CapabilityInvokeRequest request = new CapabilityInvokeRequest();
        request.setPayload(Map.of("topic", "季度经营复盘"));

        CapabilityInvokeResultVO result = service.invoke(9L, 11L, "trace-2", "127.0.0.1", request, apiKey);

        assertEquals("agent", result.getResponse().getResourceType());
        ArgumentCaptor<InvokeRequest> captor = ArgumentCaptor.forClass(InvokeRequest.class);
        verify(unifiedGatewayService).invoke(eq(11L), eq("trace-2"), eq("127.0.0.1"), captor.capture(), eq(apiKey));
        assertEquals("agent", captor.getValue().getResourceType());
        assertEquals("18", captor.getValue().getResourceId());
        assertTrue(String.valueOf(captor.getValue().getPayload().get("_lantu")).contains("contextPrompt"));
    }

    @Test
    void shouldRouteToolCallToUnderlyingMcp() {
        mockCapabilityType(21L, "skill");
        when(unifiedGatewayService.aggregatedCapabilityTools(11L, "trace-3", apiKey, "skill", "21"))
                .thenReturn(AggregatedCapabilityToolsVO.builder()
                        .entry(Map.of("resourceType", "skill", "resourceId", "21"))
                        .openAiTools(List.of())
                        .routes(List.of(ToolDispatchRouteVO.builder()
                                .unifiedFunctionName("lantu_mcp_55_search")
                                .resourceType("mcp")
                                .resourceId("55")
                                .upstreamToolName("search")
                                .build()))
                        .warnings(List.of())
                        .mcpQueriedCount(1)
                        .toolFunctionCount(1)
                        .aggregateTruncated(false)
                        .build());
        when(unifiedGatewayService.invoke(eq(11L), eq("trace-3"), eq("127.0.0.1"), any(InvokeRequest.class), eq(apiKey)))
                .thenReturn(InvokeResponse.builder()
                        .requestId("req-2")
                        .traceId("trace-3")
                        .resourceType("mcp")
                        .resourceId("55")
                        .statusCode(200)
                        .status("success")
                        .latencyMs(18L)
                        .body("{\"result\":\"ok\"}")
                        .build());

        CapabilityToolSessionRequest request = new CapabilityToolSessionRequest();
        request.setAction("call_tool");
        request.setToolName("lantu_mcp_55_search");
        request.setArguments(Map.of("q", "weather"));

        CapabilityToolSessionVO result = service.toolSession(21L, 11L, "trace-3", "127.0.0.1", request, apiKey);

        assertEquals(1, result.getRoutes().size());
        assertNotNull(result.getToolCallResponse());
        ArgumentCaptor<InvokeRequest> captor = ArgumentCaptor.forClass(InvokeRequest.class);
        verify(unifiedGatewayService).invoke(eq(11L), eq("trace-3"), eq("127.0.0.1"), captor.capture(), eq(apiKey));
        assertEquals("mcp", captor.getValue().getResourceType());
        assertEquals("55", captor.getValue().getResourceId());
        assertEquals("tools/call", captor.getValue().getPayload().get("method"));
    }

    @Test
    void shouldPersistPlatformAdapterMetaWhenCreatingCapabilityAgent() {
        when(resourceRegistryService.create(eq(11L), any(ResourceUpsertRequest.class)))
                .thenReturn(ResourceManageVO.builder()
                        .id(301L)
                        .resourceType("agent")
                        .displayName("Dify Sales Agent")
                        .resourceCode("dify-sales-agent")
                        .status("draft")
                        .registrationProtocol("openai_compatible")
                        .upstreamEndpoint("https://api.dify.ai/v1/chat-messages")
                        .transformProfile("dify_agent_app")
                        .modelAlias("dify-sales-agent")
                        .spec(Map.of(
                                "x_adapter_id", "dify",
                                "x_protocol_family", "openai_compatible"))
                        .build());

        CapabilityCreateRequest request = new CapabilityCreateRequest();
        request.setSource("https://api.dify.ai/v1/chat-messages");
        request.setDetectedType("agent");
        request.setDisplayName("Dify Sales Agent");
        request.setResourceCode("dify-sales-agent");

        service.create(11L, request);

        ArgumentCaptor<ResourceUpsertRequest> captor = ArgumentCaptor.forClass(ResourceUpsertRequest.class);
        verify(resourceRegistryService).create(eq(11L), captor.capture());
        ResourceUpsertRequest upsert = captor.getValue();
        assertEquals("openai_compatible", upsert.getRegistrationProtocol());
        assertEquals("dify_agent_app", upsert.getTransformProfile());
        assertEquals("dify", upsert.getSpec().get("x_adapter_id"));
        assertEquals("openai_compatible", upsert.getSpec().get("x_protocol_family"));
        assertFalse(upsert.getSpec().isEmpty());
    }

    @Test
    void shouldUsePlatformSuggestedPayloadWhenResolvingCapabilityAgent() {
        mockCapabilityType(301L, "agent");
        when(unifiedGatewayService.resolve(any(), eq(apiKey), eq(11L)))
                .thenReturn(ResourceResolveVO.builder()
                        .resourceType("agent")
                        .resourceId("301")
                        .displayName("Dify Sales Agent")
                        .resourceCode("dify-sales-agent")
                        .status("published")
                        .endpoint("https://api.dify.ai/v1/chat-messages")
                        .spec(Map.of(
                                "registrationProtocol", "openai_compatible",
                                "transformProfile", "dify_agent_app",
                                "x_adapter_id", "dify",
                                "x_protocol_family", "openai_compatible"))
                        .build());

        var result = service.resolve(301L, null, apiKey, 11L);

        assertEquals("hello", result.getSuggestedPayload().get("input"));
        assertEquals("test-session", result.getSuggestedPayload().get("session_id"));
        assertEquals("dify", result.getCapability().getCapabilities().get("providerPreset"));
    }

    private void mockCapabilityType(Long capabilityId, String type) {
        when(jdbcTemplate.queryForList(any(String.class), eq(capabilityId)))
                .thenReturn(List.of(Map.of("id", capabilityId, "resource_type", type)));
    }
}
