package com.lantu.connect.gateway.controller;

import com.lantu.connect.auth.mapper.UserRoleRelMapper;
import com.lantu.connect.auth.support.AccessTokenBlacklist;
import com.lantu.connect.auth.support.SessionRevocationRegistry;
import com.lantu.connect.common.config.SecurityProperties;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.exception.GlobalExceptionHandler;
import com.lantu.connect.common.filter.JwtAuthenticationFilter;
import com.lantu.connect.common.filter.UnassignedUserAccessFilter;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.common.util.JwtUtil;
import com.lantu.connect.gateway.dto.ResourceManageVO;
import com.lantu.connect.gateway.dto.ResourceVersionVO;
import com.lantu.connect.gateway.security.ApiKeyScopeService;
import com.lantu.connect.gateway.service.ResourceRegistryService;
import com.lantu.connect.gateway.service.SkillArtifactDownloadService;
import com.lantu.connect.gateway.service.SkillPackUploadService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ResourceRegistryControllerWebMvcTest {

    private MockMvc mockMvc;
    private ResourceRegistryService resourceRegistryService;
    private SkillPackUploadService skillPackUploadService;
    private SkillArtifactDownloadService skillArtifactDownloadService;
    private ApiKeyScopeService apiKeyScopeService;

    @BeforeEach
    void setUp() {
        resourceRegistryService = mock(ResourceRegistryService.class);
        skillPackUploadService = mock(SkillPackUploadService.class);
        skillArtifactDownloadService = mock(SkillArtifactDownloadService.class);
        apiKeyScopeService = mock(ApiKeyScopeService.class);
        JwtUtil jwtUtil = mock(JwtUtil.class);
        AccessTokenBlacklist blacklist = mock(AccessTokenBlacklist.class);
        SessionRevocationRegistry sessionRevocationRegistry = mock(SessionRevocationRegistry.class);
        UserRoleRelMapper userRoleRelMapper = mock(UserRoleRelMapper.class);

        SecurityProperties properties = new SecurityProperties();
        properties.setJwtEnabled(true);
        properties.setAllowHeaderUserIdFallback(false);
        when(sessionRevocationRegistry.isRevoked(any())).thenReturn(false);
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(
                jwtUtil, blacklist, sessionRevocationRegistry, properties, apiKeyScopeService);
        UnassignedUserAccessFilter unassignedFilter = new UnassignedUserAccessFilter(userRoleRelMapper, properties);

        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("1");
        when(claims.get("type", String.class)).thenReturn("access");
        when(blacklist.contains(any())).thenReturn(false);
        when(jwtUtil.parseToken("token-user")).thenReturn(claims);
        when(userRoleRelMapper.selectRoleIdsByUserId(1L)).thenReturn(List.of(10L));

        when(resourceRegistryService.create(anyLong(), any()))
                .thenReturn(ResourceManageVO.builder()
                        .id(101L)
                        .resourceType("mcp")
                        .resourceCode("demo-mcp")
                        .displayName("Demo MCP")
                        .status("draft")
                        .createTime(LocalDateTime.now())
                        .updateTime(LocalDateTime.now())
                        .build());
        when(resourceRegistryService.createVersion(anyLong(), eq(101L), any()))
                .thenReturn(ResourceVersionVO.builder()
                        .id(1L)
                        .resourceId(101L)
                        .version("v2")
                        .status("active")
                        .current(true)
                        .createTime(LocalDateTime.now())
                        .build());

        mockMvc = MockMvcBuilders
                .standaloneSetup(new ResourceRegistryController(
                        resourceRegistryService, skillPackUploadService, skillArtifactDownloadService, apiKeyScopeService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(jwtFilter, unassignedFilter)
                .build();
    }

    @Test
    void createShouldReturn200WhenBearerValid() throws Exception {
        mockMvc.perform(post("/resource-center/resources")
                        .header("Authorization", "Bearer token-user")
                        .contentType("application/json")
                        .content("""
                                {
                                  "resourceType":"mcp",
                                  "resourceCode":"demo-mcp",
                                  "displayName":"Demo MCP",
                                  "endpoint":"http://localhost:9000/mcp",
                                  "protocol":"mcp",
                                  "authType":"none"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.resourceType").value("mcp"));
    }

    @Test
    void createShouldReturn401WhenNoAuth() throws Exception {
        mockMvc.perform(post("/resource-center/resources")
                        .contentType("application/json")
                        .content("""
                                {"resourceType":"mcp","resourceCode":"demo","displayName":"Demo","endpoint":"http://localhost:9000/mcp"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ResultCode.UNAUTHORIZED.getCode()));
    }

    @Test
    void submitShouldReturn403WhenNoPermission() throws Exception {
        when(resourceRegistryService.submitForAudit(1L, 101L))
                .thenThrow(new BusinessException(ResultCode.FORBIDDEN, "仅资源拥有者可操作"));
        mockMvc.perform(post("/resource-center/resources/101/submit")
                        .header("Authorization", "Bearer token-user"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ResultCode.FORBIDDEN.getCode()));
    }

    @Test
    void deleteShouldReturn409WhenIllegalState() throws Exception {
        org.mockito.Mockito.doThrow(new BusinessException(ResultCode.ILLEGAL_STATE_TRANSITION, "审核流程中的资源不可删除"))
                .when(resourceRegistryService).delete(1L, 101L);
        mockMvc.perform(delete("/resource-center/resources/101")
                        .header("Authorization", "Bearer token-user"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ResultCode.ILLEGAL_STATE_TRANSITION.getCode()));
    }

    @Test
    void createVersionShouldReturn200() throws Exception {
        mockMvc.perform(post("/resource-center/resources/101/versions")
                        .header("Authorization", "Bearer token-user")
                        .contentType("application/json")
                        .content("""
                                {"version":"v2","makeCurrent":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.version").value("v2"))
                .andExpect(jsonPath("$.data.isCurrent").value(true));
    }
}

