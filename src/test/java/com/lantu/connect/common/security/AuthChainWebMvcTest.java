package com.lantu.connect.common.security;

import com.lantu.connect.auth.mapper.UserRoleRelMapper;
import com.lantu.connect.auth.support.AccessTokenBlacklist;
import com.lantu.connect.auth.support.SessionRevocationRegistry;
import com.lantu.connect.common.config.SecurityProperties;
import com.lantu.connect.common.exception.GlobalExceptionHandler;
import com.lantu.connect.common.filter.JwtAuthenticationFilter;
import com.lantu.connect.common.filter.UnassignedUserAccessFilter;
import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.util.JwtUtil;
import com.lantu.connect.gateway.security.ApiKeyScopeService;
import com.lantu.connect.dashboard.controller.DashboardController;
import com.lantu.connect.dashboard.dto.UsageStatsVO;
import com.lantu.connect.dashboard.service.DashboardService;
import com.lantu.connect.useractivity.controller.UserActivityController;
import com.lantu.connect.useractivity.service.UserActivityService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthChainWebMvcTest {

    private MockMvc mockMvc;

    private JwtUtil jwtUtil;
    private AccessTokenBlacklist accessTokenBlacklist;
    private SessionRevocationRegistry sessionRevocationRegistry;
    private UserRoleRelMapper userRoleRelMapper;
    private CasbinAuthorizationService casbinAuthorizationService;
    private UserActivityService userActivityService;
    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        jwtUtil = mock(JwtUtil.class);
        accessTokenBlacklist = mock(AccessTokenBlacklist.class);
        sessionRevocationRegistry = mock(SessionRevocationRegistry.class);
        userRoleRelMapper = mock(UserRoleRelMapper.class);
        casbinAuthorizationService = mock(CasbinAuthorizationService.class);
        userActivityService = mock(UserActivityService.class);
        dashboardService = mock(DashboardService.class);

        SecurityProperties properties = new SecurityProperties();
        properties.setJwtEnabled(true);
        properties.setAllowHeaderUserIdFallback(false);
        when(sessionRevocationRegistry.isRevoked(anyString())).thenReturn(false);
        ApiKeyScopeService apiKeyScopeService = mock(ApiKeyScopeService.class);
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(
                jwtUtil, accessTokenBlacklist, sessionRevocationRegistry, properties, apiKeyScopeService);
        UnassignedUserAccessFilter unassignedFilter = new UnassignedUserAccessFilter(userRoleRelMapper, properties);

        DashboardController dashboardController = new DashboardController(dashboardService);
        DashboardController proxiedDashboard = proxyWithPermissionAspect(dashboardController, casbinAuthorizationService);

        UserActivityController userActivityController = new UserActivityController(userActivityService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(userActivityController, proxiedDashboard)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(jwtFilter, unassignedFilter)
                .build();

        Claims userClaims = mock(Claims.class);
        when(userClaims.getSubject()).thenReturn("1");
        when(userClaims.get("type", String.class)).thenReturn("access");
        Claims monitorClaims = mock(Claims.class);
        when(monitorClaims.getSubject()).thenReturn("2");
        when(monitorClaims.get("type", String.class)).thenReturn("access");

        when(accessTokenBlacklist.contains(anyString())).thenReturn(false);
        when(jwtUtil.parseToken("token-user")).thenReturn(userClaims);
        when(jwtUtil.parseToken("token-monitor")).thenReturn(monitorClaims);

        when(userRoleRelMapper.selectRoleIdsByUserId(1L)).thenReturn(List.of(10L));
        when(userRoleRelMapper.selectRoleIdsByUserId(2L)).thenReturn(List.of(20L));

        when(casbinAuthorizationService.hasPermissions(eq(1L), any(String[].class), any())).thenReturn(false);
        when(casbinAuthorizationService.hasPermissions(eq(2L), any(String[].class), any())).thenReturn(true);

        when(userActivityService.pageUsageRecords(anyLong(), anyInt(), anyInt(), any()))
                .thenReturn(PageResult.of(Collections.emptyList(), 0, 1, 20));
        when(dashboardService.usageStats(anyString()))
                .thenReturn(UsageStatsVO.builder()
                        .aggregates(Map.of("total", 1))
                        .series(Collections.emptyList())
                        .breakdown(Collections.emptyMap())
                        .build());
    }

    @Test
    void usageRecordsShouldReturn200WhenBearerValid() throws Exception {
        mockMvc.perform(get("/user/usage-records")
                        .header("Authorization", "Bearer token-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void usageRecordsShouldReturn401WhenNoAuth() throws Exception {
        mockMvc.perform(get("/user/usage-records"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1002));
    }

    @Test
    void dashboardUsageStatsShouldReturn403WhenPermissionMissing() throws Exception {
        mockMvc.perform(get("/dashboard/usage-stats")
                        .param("range", "7d")
                        .header("Authorization", "Bearer token-user"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003));
    }

    @Test
    void dashboardUsageStatsShouldReturn200WhenPermissionGranted() throws Exception {
        mockMvc.perform(get("/dashboard/usage-stats")
                        .param("range", "7d")
                        .header("Authorization", "Bearer token-monitor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private static DashboardController proxyWithPermissionAspect(DashboardController controller,
                                                                 CasbinAuthorizationService casbinAuthorizationService) {
        AspectJProxyFactory factory = new AspectJProxyFactory(controller);
        factory.addAspect(new RequirePermissionAspect(casbinAuthorizationService));
        return factory.getProxy();
    }
}
