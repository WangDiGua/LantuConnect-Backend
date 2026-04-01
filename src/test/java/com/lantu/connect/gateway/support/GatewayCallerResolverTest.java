package com.lantu.connect.gateway.support;

import com.lantu.connect.common.security.GatewayAuthDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GatewayCallerResolverTest {

    private final GatewayCallerResolver resolver = new GatewayCallerResolver();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolvesNumericPrincipalAsJwtUser() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("42", null, Collections.emptyList()));
        assertEquals(42L, resolver.resolveTrustedUserIdOrNull());
    }

    @Test
    void resolvesApiKeyOwnerFromDetails() {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("api-key", null, Collections.emptyList());
        auth.setDetails(new GatewayAuthDetails(99L));
        SecurityContextHolder.getContext().setAuthentication(auth);
        assertEquals(99L, resolver.resolveTrustedUserIdOrNull());
    }

    @Test
    void apiKeyWithoutOwnerUserYieldsNull() {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("api-key", null, Collections.emptyList());
        auth.setDetails(new GatewayAuthDetails(null));
        SecurityContextHolder.getContext().setAuthentication(auth);
        assertNull(resolver.resolveTrustedUserIdOrNull());
    }
}
