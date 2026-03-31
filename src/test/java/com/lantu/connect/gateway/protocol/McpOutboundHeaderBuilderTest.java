package com.lantu.connect.gateway.protocol;

import com.lantu.connect.gateway.protocol.auth.Oauth2ClientCredentialsTokenService;
import com.lantu.connect.gateway.protocol.secret.GatewaySecretRefResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.StandardEnvironment;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpOutboundHeaderBuilderTest {

    @Mock
    private Oauth2ClientCredentialsTokenService oauth2;

    @Test
    void bearerUsesTokenFromAuthConfig() {
        GatewaySecretRefResolver secrets = new GatewaySecretRefResolver(new StandardEnvironment());
        McpOutboundHeaderBuilder b = new McpOutboundHeaderBuilder(secrets, oauth2);
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put(McpOutboundHeaderBuilder.REGISTRY_AUTH_TYPE_KEY, "bearer");
        spec.put("token", "secret-one");
        assertThat(b.buildHeaders(spec)).containsEntry("Authorization", "Bearer secret-one");
    }

    @Test
    void oauth2UsesAccessToken() {
        GatewaySecretRefResolver secrets = new GatewaySecretRefResolver(new StandardEnvironment());
        when(oauth2.getAccessToken(anyString(), anyString(), anyString(), nullable(String.class))).thenReturn("tok-99");
        McpOutboundHeaderBuilder b = new McpOutboundHeaderBuilder(secrets, oauth2);
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put(McpOutboundHeaderBuilder.REGISTRY_AUTH_TYPE_KEY, "oauth2_client");
        spec.put("tokenUrl", "https://idp/token");
        spec.put("clientId", "c");
        spec.put("clientSecret", "s");
        assertThat(b.buildHeaders(spec)).containsEntry("Authorization", "Bearer tok-99");
    }

    @Test
    void customHeadersMergeAfterBearer() {
        GatewaySecretRefResolver secrets = new GatewaySecretRefResolver(new StandardEnvironment());
        McpOutboundHeaderBuilder b = new McpOutboundHeaderBuilder(secrets, oauth2);
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put(McpOutboundHeaderBuilder.REGISTRY_AUTH_TYPE_KEY, "bearer");
        spec.put("token", "t");
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("X-Foo", "bar");
        spec.put("headers", headers);
        assertThat(b.buildHeaders(spec))
                .containsEntry("Authorization", "Bearer t")
                .containsEntry("X-Foo", "bar");
    }
}
