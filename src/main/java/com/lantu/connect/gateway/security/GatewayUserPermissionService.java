package com.lantu.connect.gateway.security;

import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.common.security.CasbinAuthorizationService;
import com.lantu.connect.common.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class GatewayUserPermissionService {

    private final CasbinAuthorizationService casbinAuthorizationService;

    public boolean canAccessType(Long userId, String resourceType) {
        if (userId == null) {
            return true;
        }
        if (!StringUtils.hasText(resourceType)) {
            return false;
        }
        if (casbinAuthorizationService.hasAnyRole(userId, new String[]{"platform_admin"})) {
            return true;
        }
        String type = resourceType.trim().toLowerCase(Locale.ROOT);
        return switch (type) {
            case "agent" -> casbinAuthorizationService.hasPermissions(
                    userId, new String[]{"agent:read", "skill:read"}, RequirePermission.LogicalOperator.OR);
            case "skill", "mcp" -> casbinAuthorizationService.hasPermissions(
                    userId, new String[]{"skill:read"}, RequirePermission.LogicalOperator.OR);
            case "app" -> casbinAuthorizationService.hasPermissions(
                    userId, new String[]{"app:view"}, RequirePermission.LogicalOperator.OR);
            case "dataset" -> casbinAuthorizationService.hasPermissions(
                    userId, new String[]{"dataset:read"}, RequirePermission.LogicalOperator.OR);
            default -> false;
        };
    }

    public void ensureAccess(Long userId, String resourceType) {
        if (!canAccessType(userId, resourceType)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前用户无该资源类型访问权限");
        }
    }
}
