package com.lantu.connect.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.gateway.dto.AggregatedCapabilityToolsVO;
import com.lantu.connect.gateway.dto.ResourceResolveVO;
import com.lantu.connect.gateway.protocol.ProtocolInvokeResult;
import com.lantu.connect.gateway.protocol.ProtocolInvokerRegistry;
import com.lantu.connect.gateway.security.ApiKeyScopeService;
import com.lantu.connect.usermgmt.entity.ApiKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GatewayBindingExpansionServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private ProtocolInvokerRegistry protocolInvokerRegistry;
    @Mock
    private ApiKeyScopeService apiKeyScopeService;

    private GatewayBindingExpansionService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new GatewayBindingExpansionService(jdbcTemplate, objectMapper, protocolInvokerRegistry, apiKeyScopeService);
    }

    @Test
    void listAgentBoundMcpIds_queriesRelationTable() {
        when(jdbcTemplate.query(anyString(), ArgumentMatchers.<RowMapper<Long>>any(), eq(5L)))
                .thenReturn(List.of(10L, 11L));
        assertThat(service.listAgentBoundMcpIds(5L)).containsExactly(10L, 11L);
    }

    @Test
    void listMcpsDependingOnSkill_queriesInverseRelation() {
        when(jdbcTemplate.query(anyString(), ArgumentMatchers.<RowMapper<Long>>any(), eq(9L)))
                .thenReturn(List.of(30L));
        assertThat(service.listMcpsDependingOnSkill(9L)).containsExactly(30L);
    }

    @Test
    void aggregateMcpTools_emptyIds_returnsEmptyWithoutInvokerCalls() {
        ApiKey key = new ApiKey();
        key.setId("key-1");
        AggregatedCapabilityToolsVO vo = service.aggregateMcpTools(
                List.of(),
                Map.of("resourceType", "agent", "resourceId", "1"),
                100L,
                "trace",
                key,
                id -> {
                    throw new AssertionError("should not resolve");
                });
        assertThat(vo.getOpenAiTools()).isEmpty();
        assertThat(vo.getRoutes()).isEmpty();
        assertThat(vo.getWarnings()).isEmpty();
        assertThat(vo.getEntry()).containsEntry("resourceType", "agent").containsEntry("resourceId", "1");
        verifyNoInteractions(protocolInvokerRegistry);
    }

    @Test
    void aggregateMcpTools_mergesToolsFromMcpList() throws Exception {
        ApiKey key = new ApiKey();
        key.setId("key-1");
        ResourceResolveVO resolved = ResourceResolveVO.builder()
                .resourceType("mcp")
                .resourceId("10")
                .resourceCode("c")
                .status("published")
                .invokeType("mcp")
                .endpoint("http://localhost/mcp")
                .spec(Map.of())
                .build();
        when(protocolInvokerRegistry.invoke(
                eq("mcp"),
                eq("http://localhost/mcp"),
                eq(45),
                anyString(),
                ArgumentMatchers.<Map<String, Object>>any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any()))
                .thenReturn(new ProtocolInvokeResult(
                        200,
                        """
                                {"result":{"tools":[{"name":"alpha","description":"d","inputSchema":{"type":"object","properties":{}}}]}}
                                """,
                        1L));
        AggregatedCapabilityToolsVO vo = service.aggregateMcpTools(
                List.of(10L),
                null,
                100L,
                "t1",
                key,
                id -> {
                    assertThat(id).isEqualTo(10L);
                    return resolved;
                });
        assertThat(vo.getOpenAiTools()).hasSize(1);
        assertThat(vo.getRoutes()).hasSize(1);
        assertThat(vo.getWarnings()).isEmpty();
        @SuppressWarnings("unchecked")
        Map<String, Object> fn = (Map<String, Object>) vo.getOpenAiTools().get(0).get("function");
        assertThat(fn.get("name")).isEqualTo("lantu_mcp_10_alpha");
    }
}
