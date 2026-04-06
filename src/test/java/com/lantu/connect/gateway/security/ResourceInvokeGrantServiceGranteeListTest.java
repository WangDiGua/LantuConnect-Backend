package com.lantu.connect.gateway.security;

import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.common.security.CasbinAuthorizationService;
import com.lantu.connect.common.util.UserDisplayNameResolver;
import com.lantu.connect.gateway.entity.ResourceInvokeGrant;
import com.lantu.connect.gateway.mapper.ResourceInvokeGrantMapper;
import com.lantu.connect.notification.service.SystemNotificationFacade;
import com.lantu.connect.usermgmt.entity.ApiKey;
import com.lantu.connect.usermgmt.mapper.ApiKeyMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceInvokeGrantServiceGranteeListTest {

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

    @InjectMocks
    private ResourceInvokeGrantService resourceInvokeGrantService;

    @Test
    void listActiveGrantsForGranteeApiKey_whenKeyWrongOwner_throwsNotFound() {
        ApiKey key = new ApiKey();
        key.setId("k1");
        key.setOwnerType("user");
        key.setOwnerId("999");
        when(apiKeyMapper.selectById("k1")).thenReturn(key);

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> resourceInvokeGrantService.listActiveGrantsForGranteeApiKey(1L, "k1", "mcp"));

        assertEquals(ResultCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void listActiveGrantsForGranteeApiKey_whenOwner_ok_andMapsVo() {
        ApiKey key = new ApiKey();
        key.setId("k1");
        key.setOwnerType("user");
        key.setOwnerId("1");
        when(apiKeyMapper.selectById("k1")).thenReturn(key);
        ResourceInvokeGrant g = new ResourceInvokeGrant();
        g.setId(1L);
        g.setResourceType("mcp");
        g.setResourceId(10L);
        g.setGranteeType("api_key");
        g.setGranteeId("k1");
        g.setActions(List.of("invoke"));
        g.setStatus("active");
        g.setGrantedByUserId(2L);
        when(resourceInvokeGrantMapper.selectList(any())).thenReturn(List.of(g));
        when(userDisplayNameResolver.resolveDisplayNames(any())).thenReturn(Map.of(2L, "alice"));

        var vos = resourceInvokeGrantService.listActiveGrantsForGranteeApiKey(1L, "k1", "mcp");

        assertEquals(1, vos.size());
        assertEquals("mcp", vos.get(0).getResourceType());
        assertEquals(10L, vos.get(0).getResourceId());
    }
}
