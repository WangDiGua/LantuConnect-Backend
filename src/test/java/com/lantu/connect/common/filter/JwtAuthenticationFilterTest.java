package com.lantu.connect.common.filter;

import com.lantu.connect.auth.support.AccessTokenBlacklist;
import com.lantu.connect.auth.support.SessionRevocationRegistry;
import com.lantu.connect.common.config.SecurityProperties;
import com.lantu.connect.common.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.jsonwebtoken.Claims;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    @Test
    void shouldAllowApiKeyRequestWithoutJwt() throws Exception {
        SecurityProperties properties = new SecurityProperties();
        properties.setJwtEnabled(true);
        properties.setAllowHeaderUserIdFallback(false);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                mock(JwtUtil.class),
                mock(AccessTokenBlacklist.class),
                mock(SessionRevocationRegistry.class),
                properties
        );

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/catalog/resources");
        request.setServletPath("/catalog/resources");
        request.addHeader("X-Api-Key", "sk-test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());
        assertEquals(200, response.getStatus());
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
                properties
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
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil, blacklist, revocation, properties);

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
