package com.lantu.connect.gateway.security;

import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.common.security.CasbinAuthorizationService;
import com.lantu.connect.common.util.UserDisplayNameResolver;
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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceInvokeGrantServicePolicyTest {

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
    void ensureApiKeyGranted_openPlatform_skipsGrantRow() {
        doReturn(List.of(Map.of(
                "created_by", 9001L,
                "access_policy", "open_platform")))
                .when(jdbcTemplate).queryForList(anyString(), any(Object.class), any(Object.class));
        ApiKey apiKey = userApiKey("k1", "8001");

        assertThatCode(() -> resourceInvokeGrantService.ensureApiKeyGranted(
                apiKey, "invoke", "agent", 101L, 8001L))
                .doesNotThrowAnyException();
    }

    @Test
    void ensureApiKeyGranted_grantRequired_withoutGrant_forbidden() {
        doReturn(List.of(Map.of(
                "created_by", 9001L,
                "access_policy", "grant_required")))
                .when(jdbcTemplate).queryForList(anyString(), any(Object.class), any(Object.class));
        when(resourceInvokeGrantMapper.selectList(any())).thenReturn(List.of());
        ApiKey apiKey = userApiKey("k2", "8001");

        assertThatThrownBy(() -> resourceInvokeGrantService.ensureApiKeyGranted(
                apiKey, "invoke", "agent", 101L, 8001L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ResultCode.FORBIDDEN.getCode());
    }

    @Test
    void ensureMayReviewGrantApplication_reviewerCrossDepartment_ok() {
        doReturn(List.of(Map.of(
                "created_by", 99L,
                "access_policy", "grant_required")))
                .when(jdbcTemplate).queryForList(anyString(), any(Object.class), any(Object.class));
        when(casbinAuthorizationService.hasAnyRole(10L, new String[]{"platform_admin", "admin"})).thenReturn(false);
        when(casbinAuthorizationService.hasAnyRole(10L, new String[]{"reviewer"})).thenReturn(true);

        assertThatCode(() -> resourceInvokeGrantService.ensureMayReviewGrantApplication(10L, "agent", 5L))
                .doesNotThrowAnyException();
    }

    private static ApiKey userApiKey(String id, String ownerUserId) {
        ApiKey k = new ApiKey();
        k.setId(id);
        k.setOwnerType("user");
        k.setOwnerId(ownerUserId);
        k.setStatus("active");
        return k;
    }
}
