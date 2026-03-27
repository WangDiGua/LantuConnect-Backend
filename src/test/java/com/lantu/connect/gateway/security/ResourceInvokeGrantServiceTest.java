package com.lantu.connect.gateway.security;

import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.common.security.CasbinAuthorizationService;
import com.lantu.connect.common.util.UserDisplayNameResolver;
import com.lantu.connect.gateway.mapper.ResourceInvokeGrantMapper;
import com.lantu.connect.notification.service.SystemNotificationFacade;
import com.lantu.connect.usermgmt.entity.ApiKey;
import com.lantu.connect.usermgmt.mapper.ApiKeyMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceInvokeGrantServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private ResourceInvokeGrantMapper resourceInvokeGrantMapper;
    @Mock
    private ApiKeyMapper apiKeyMapper;
    @Mock
    private CasbinAuthorizationService casbinAuthorizationService;
    @Mock
    private UserDisplayNameResolver userDisplayNameResolver;
    @Mock
    private SystemNotificationFacade systemNotificationFacade;

    private ResourceInvokeGrantService service;

    @BeforeEach
    void setUp() {
        service = new ResourceInvokeGrantService(
                jdbcTemplate,
                resourceInvokeGrantMapper,
                apiKeyMapper,
                casbinAuthorizationService,
                userDisplayNameResolver,
                systemNotificationFacade);
    }

    @Test
    void shouldAllowWhenApiKeyOwnerIsResourceOwner() {
        ApiKey apiKey = new ApiKey();
        apiKey.setId("key-1");
        apiKey.setOwnerType("user");
        apiKey.setOwnerId("10");

        when(jdbcTemplate.queryForList(any(String.class), eq("mcp"), eq(101L)))
                .thenReturn(List.of(Map.of("created_by", 10L)));

        service.ensureApiKeyGranted(apiKey, "invoke", "mcp", 101L, null);
    }

    @Test
    void shouldRejectWhenThirdPartyHasNoGrant() {
        ApiKey apiKey = new ApiKey();
        apiKey.setId("key-2");
        apiKey.setOwnerType("user");
        apiKey.setOwnerId("99");

        when(jdbcTemplate.queryForList(any(String.class), eq("mcp"), eq(101L)))
                .thenReturn(List.of(Map.of("created_by", 10L)));
        when(resourceInvokeGrantMapper.selectList(any())).thenReturn(Collections.emptyList());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.ensureApiKeyGranted(apiKey, "invoke", "mcp", 101L, null));
        assertEquals(ResultCode.FORBIDDEN.getCode(), ex.getCode());
    }
}
