package com.lantu.connect.common.filter;

import com.lantu.connect.auth.support.AccessTokenBlacklist;
import com.lantu.connect.auth.support.SessionRevocationRegistry;
import com.lantu.connect.common.config.SecurityProperties;
import com.lantu.connect.common.util.JwtUtil;
import com.lantu.connect.gateway.security.ApiKeyScopeService;
import com.lantu.connect.usermgmt.entity.ApiKey;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.HttpServletRequest;

import io.jsonwebtoken.Claims;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    @Test
    void shouldAllowValidUserOwnedApiKeyWithoutJwt() throws Exception {
        SecurityProperties properties = new SecurityProperties();
        properties.setJwtEnabled(true);
        properties.setAllowHeaderUserIdFallback(false);
        ApiKeyScopeService apiKeyScopeService = mock(ApiKeyScopeService.class);
        ApiKey active = new ApiKey();
        active.setOwnerType("user");
        active.setOwnerId("7");
        when(apiKeyScopeService.authenticateOrNull("sk-good")).thenReturn(active);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                mock(JwtUtil.class),
                mock(AccessTokenBlacklist.class),
                mock(SessionRevocationRegistry.class),
                properties,
                apiKeyScopeService
        );

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/catalog/resources");
        request.setServletPath("/catalog/resources");
        request.addHeader("X-Api-Key", "sk-good");
        request.addHeader("X-User-Id", "999");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> downstreamUserId = new AtomicReference<>();
        filter.doFilter(request, response, (req, res) ->
                downstreamUserId.set(((HttpServletRequest) req).getHeader("X-User-Id")));

        assertEquals(200, response.getStatus());
        assertEquals("7", downstreamUserId.get());
    }

    @Test
    void orgOwnedApiKeyShouldStripClientUserIdHeader() throws Exception {
        SecurityProperties properties = new SecurityProperties();
        properties.setJwtEnabled(true);
        ApiKeyScopeService apiKeyScopeService = mock(ApiKeyScopeService.class);
        ApiKey orgKey = new ApiKey();
        orgKey.setOwnerType("org");
        orgKey.setOwnerId("dept-1");
        when(apiKeyScopeService.authenticateOrNull("sk-org")).thenReturn(orgKey);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                mock(JwtUtil.class),
                mock(AccessTokenBlacklist.class),
                mock(SessionRevocationRegistry.class),
                properties,
                apiKeyScopeService
        );

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/catalog/resources");
        request.setServletPath("/catalog/resources");
        request.addHeader("X-Api-Key", "sk-org");
        request.addHeader("X-User-Id", "999");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> downstreamUserId = new AtomicReference<>("unset");
        filter.doFilter(request, response, (req, res) ->
                downstreamUserId.set(((HttpServletRequest) req).getHeader("X-User-Id")));

        assertEquals(200, response.getStatus());
        assertNull(downstreamUserId.get());
    }

    @Test
    void shouldRejectInvalidApiKeyWithoutJwt() throws Exception {
        SecurityProperties properties = new SecurityProperties();
        properties.setJwtEnabled(true);
        properties.setAllowHeaderUserIdFallback(false);
        ApiKeyScopeService apiKeyScopeService = mock(ApiKeyScopeService.class);
        when(apiKeyScopeService.authenticateOrNull("bad")).thenReturn(null);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                mock(JwtUtil.class),
                mock(AccessTokenBlacklist.class),
                mock(SessionRevocationRegistry.class),
                properties,
                apiKeyScopeService
        );

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/catalog/resources");
        request.setServletPath("/catalog/resources");
        request.addHeader("X-Api-Key", "bad");
        request.addHeader("X-User-Id", "1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());
        assertEquals(401, response.getStatus());
    }

    @Test
    void jwtValidShouldNotCallApiKeyLookupEvenIfStaleKeyHeaderPresent() throws Exception {
        SecurityProperties properties = new SecurityProperties();
        properties.setJwtEnabled(true);
        ApiKeyScopeService apiKeyScopeService = mock(ApiKeyScopeService.class);
        JwtUtil jwtUtil = mock(JwtUtil.class);
        AccessTokenBlacklist blacklist = mock(AccessTokenBlacklist.class);
        SessionRevocationRegistry revocation = mock(SessionRevocationRegistry.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                jwtUtil, blacklist, revocation, properties, apiKeyScopeService
        );

        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("3");
        when(claims.get("type", String.class)).thenReturn(null);
        when(claims.get("sid", String.class)).thenReturn(null);
        when(blacklist.contains("access-tok")).thenReturn(false);
        when(jwtUtil.parseToken("access-tok")).thenReturn(claims);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/catalog/resources");
        request.setServletPath("/catalog/resources");
        request.addHeader("Authorization", "Bearer access-tok");
        request.addHeader("X-Api-Key", "stale-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> downstreamUserId = new AtomicReference<>();
        filter.doFilter(request, response, (req, res) ->
                downstreamUserId.set(((HttpServletRequest) req).getHeader("X-User-Id")));

        assertEquals(200, response.getStatus());
        assertEquals("3", downstreamUserId.get());
        verify(apiKeyScopeService, never()).authenticateOrNull(any());
    }

    @Test
    void shouldRejectRequestWithoutJwtAndApiKey() throws Exception {
        SecurityProperties properties = new SecurityProperties();
        properties.setJwtEnabled(true);
        properties.setAllowHeaderUserIdFallback(false);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                mock(JwtUtil.class),
                mock(AccessTokenBlacklist.class),
                mock(SessionRevocationRegistry.class),
                properties,
                mock(ApiKeyScopeService.class)
        );

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/user-mgmt/users");
        request.setServletPath("/user-mgmt/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());
        assertEquals(401, response.getStatus());
    }

    @Test
    void shouldRejectBearerWhenSessionRevoked() throws Exception {
        SecurityProperties properties = new SecurityProperties();
        properties.setJwtEnabled(true);
        properties.setAllowHeaderUserIdFallback(false);
        JwtUtil jwtUtil = mock(JwtUtil.class);
        AccessTokenBlacklist blacklist = mock(AccessTokenBlacklist.class);
        SessionRevocationRegistry revocation = mock(SessionRevocationRegistry.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                jwtUtil, blacklist, revocation, properties, mock(ApiKeyScopeService.class));

        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("1");
        when(claims.get("type", String.class)).thenReturn(null);
        when(claims.get("sid", String.class)).thenReturn("sess-1");
        when(blacklist.contains("tok")).thenReturn(false);
        when(jwtUtil.parseToken("tok")).thenReturn(claims);
        when(revocation.isRevoked("sess-1")).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/user-mgmt/users");
        request.setServletPath("/user-mgmt/users");
        request.addHeader("Authorization", "Bearer tok");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());
        assertEquals(401, response.getStatus());
    }
}
