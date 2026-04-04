package com.lantu.connect.gateway.controller;

import com.lantu.connect.auth.support.AccessTokenBlacklist;
import com.lantu.connect.auth.support.SessionRevocationRegistry;
import com.lantu.connect.common.config.SecurityProperties;
import com.lantu.connect.common.exception.GlobalExceptionHandler;
import com.lantu.connect.common.filter.JwtAuthenticationFilter;
import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.security.CasbinAuthorizationService;
import com.lantu.connect.common.security.RequirePermissionAspect;
import com.lantu.connect.common.util.JwtUtil;
import com.lantu.connect.gateway.dto.SkillExternalCatalogItemVO;
import com.lantu.connect.gateway.security.ApiKeyScopeService;
import com.lantu.connect.gateway.service.SkillExternalCatalogService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SkillExternalCatalogControllerWebMvcTest {

    private MockMvc mockMvc;
    private CasbinAuthorizationService casbinAuthorizationService;

    @BeforeEach
    void setUp() {
        casbinAuthorizationService = mock(CasbinAuthorizationService.class);
        JwtUtil jwtUtil = mock(JwtUtil.class);
        AccessTokenBlacklist blacklist = mock(AccessTokenBlacklist.class);
        SessionRevocationRegistry sessionRevocationRegistry = mock(SessionRevocationRegistry.class);
        ApiKeyScopeService apiKeyScopeService = mock(ApiKeyScopeService.class);

        SecurityProperties properties = new SecurityProperties();
        properties.setJwtEnabled(true);
        properties.setAllowHeaderUserIdFallback(false);
        when(sessionRevocationRegistry.isRevoked(any())).thenReturn(false);
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(
                jwtUtil, blacklist, sessionRevocationRegistry, properties, apiKeyScopeService);
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("1");
        when(claims.get("type", String.class)).thenReturn("access");
        when(blacklist.contains(any())).thenReturn(false);
        when(jwtUtil.parseToken("token-admin")).thenReturn(claims);
        SkillExternalCatalogService catalogService = mock(SkillExternalCatalogService.class);
        when(catalogService.listCatalogPage(isNull(), eq(1), eq(20))).thenReturn(PageResult.of(List.of(
                SkillExternalCatalogItemVO.builder()
                        .id("row-1")
                        .name("Test Pack")
                        .summary("Summary")
                        .packUrl("https://cdn.example.com/a.zip")
                        .licenseNote("MIT")
                        .sourceUrl("https://example.com")
                        .stars(10)
                        .build()), 1, 1, 20));

        SkillExternalCatalogController controller = new SkillExternalCatalogController(catalogService);
        SkillExternalCatalogController proxied = proxyWithPermissionAspect(controller, casbinAuthorizationService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(proxied)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(jwtFilter)
                .build();
    }

    @Test
    void listShouldReturn403WhenMissingSystemConfig() throws Exception {
        when(casbinAuthorizationService.hasPermissions(eq(1L), any(String[].class), any())).thenReturn(false);

        mockMvc.perform(get("/resource-center/skill-external-catalog")
                        .header("Authorization", "Bearer token-admin"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003));
    }

    @Test
    void listShouldReturnPageWhenGranted() throws Exception {
        when(casbinAuthorizationService.hasPermissions(eq(1L), any(String[].class), any())).thenReturn(true);

        mockMvc.perform(get("/resource-center/skill-external-catalog")
                        .header("Authorization", "Bearer token-admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.list[0].id").value("row-1"))
                .andExpect(jsonPath("$.data.list[0].packUrl").value("https://cdn.example.com/a.zip"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(20));
    }

    private static SkillExternalCatalogController proxyWithPermissionAspect(SkillExternalCatalogController controller,
                                                                            CasbinAuthorizationService svc) {
        AspectJProxyFactory factory = new AspectJProxyFactory(controller);
        factory.addAspect(new RequirePermissionAspect(svc));
        return factory.getProxy();
    }
}
