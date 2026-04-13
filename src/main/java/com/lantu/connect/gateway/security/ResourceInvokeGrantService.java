package com.lantu.connect.gateway.security;

import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.common.security.CasbinAuthorizationService;
import com.lantu.connect.usermgmt.entity.ApiKey;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 资源调用授权（历史 Grant / accessPolicy 豁免）已下线：对已发布资源仅校验资源存在与上层 API Key scope；
 * 保留与审核发布相关的「谁可代管资源」校验。
 */
@Service
@RequiredArgsConstructor
public class ResourceInvokeGrantService {

    private final JdbcTemplate jdbcTemplate;
    private final CasbinAuthorizationService casbinAuthorizationService;

    public boolean isInvokeGrantSatisfied(ApiKey apiKey, String resourceType, Long resourceId, Long callerUserId) {
        if (apiKey == null) {
            return false;
        }
        try {
            ensureApiKeyGranted(apiKey, "invoke", resourceType, resourceId, callerUserId);
            return true;
        } catch (BusinessException ex) {
            if (ex.getCode() == ResultCode.FORBIDDEN.getCode() || ex.getCode() == ResultCode.NOT_FOUND.getCode()) {
                return false;
            }
            throw ex;
        }
    }

    public void ensureApiKeyGranted(ApiKey apiKey, String action, String resourceType, Long resourceId, Long callerUserId) {
        if (apiKey == null) {
            return;
        }
        if (isPlatformOwner(apiKey)) {
            return;
        }
        Long ownerUserId = resolveResourceOwnerUserId(resourceType, resourceId);
        if (ownerUserId == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "资源不存在或不可访问");
        }
        if (isApiKeyOwnedByAgentResource(apiKey, resourceId)) {
            return;
        }
        if (isApiKeyOwnedByUser(apiKey, ownerUserId)) {
            return;
        }
        if (callerUserId != null && callerUserId.equals(ownerUserId)) {
            return;
        }
        // 不再校验 t_resource_invoke_grant / accessPolicy；由 API Key scope + 资源生命周期约束。
    }

    public boolean canCatalog(ApiKey apiKey, String resourceType, Long resourceId, Long callerUserId) {
        try {
            ensureApiKeyGranted(apiKey, "catalog", resourceType, resourceId, callerUserId);
            return true;
        } catch (BusinessException ex) {
            if (ex.getCode() == ResultCode.FORBIDDEN.getCode()) {
                return false;
            }
            throw ex;
        }
    }

    public void ensureMayPublishAuditedResource(Long operatorUserId, String resourceType, Long resourceId) {
        if (operatorUserId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未认证用户无法发布");
        }
        String type = normalizeType(resourceType);
        Long ownerUserId = resolveResourceOwnerUserId(type, resourceId);
        if (ownerUserId == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "资源不存在");
        }
        ensureCanManageGrant(operatorUserId, ownerUserId);
    }

    private void ensureCanManageGrant(Long operatorUserId, Long ownerUserId) {
        if (operatorUserId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未认证用户无法管理授权");
        }
        if (ownerUserId != null && ownerUserId.equals(operatorUserId)) {
            return;
        }
        if (casbinAuthorizationService.hasAnyRole(operatorUserId, new String[]{"platform_admin", "admin"})) {
            return;
        }
        if (casbinAuthorizationService.hasAnyRole(operatorUserId, new String[]{"reviewer"})) {
            return;
        }
        throw new BusinessException(ResultCode.FORBIDDEN, "仅资源拥有者或管理员可管理授权");
    }

    private Long resolveResourceOwnerUserId(String resourceType, Long resourceId) {
        if (resourceId == null) {
            return null;
        }
        String type = normalizeType(resourceType);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT created_by FROM t_resource
                WHERE deleted = 0 AND resource_type = ? AND id = ? LIMIT 1
                """, type, resourceId);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        Object createdBy = row.get("created_by");
        if (createdBy == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(createdBy));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String normalizeType(String type) {
        if (!StringUtils.hasText(type)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "resourceType 不能为空");
        }
        return type.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isPlatformOwner(ApiKey apiKey) {
        return "platform".equalsIgnoreCase(apiKey.getOwnerType())
                || "system".equalsIgnoreCase(apiKey.getOwnerType());
    }

    private static boolean isApiKeyOwnedByUser(ApiKey apiKey, Long userId) {
        if (!"user".equalsIgnoreCase(apiKey.getOwnerType())) {
            return false;
        }
        if (userId == null || !StringUtils.hasText(apiKey.getOwnerId())) {
            return false;
        }
        return String.valueOf(userId).equals(apiKey.getOwnerId().trim());
    }

    private static boolean isApiKeyOwnedByAgentResource(ApiKey apiKey, Long resourceId) {
        if (!"agent".equalsIgnoreCase(apiKey.getOwnerType())) {
            return false;
        }
        if (resourceId == null || !StringUtils.hasText(apiKey.getOwnerId())) {
            return false;
        }
        return String.valueOf(resourceId).equals(apiKey.getOwnerId().trim());
    }
}
