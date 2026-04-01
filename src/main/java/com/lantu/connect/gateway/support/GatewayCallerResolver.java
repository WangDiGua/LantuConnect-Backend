package com.lantu.connect.gateway.support;

import com.lantu.connect.common.security.GatewayAuthDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 网关类接口可信用户 ID：只认 JWT / 过滤器注入的身份，不直接采信客户端原始 X-User-Id 头。
 */
@Component
public class GatewayCallerResolver {

    /**
     * @return JWT subject、用户主体 API Key 的 ownerUserId；组织等非用户主体为 null
     */
    public Long resolveTrustedUserIdOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            return null;
        }
        String principal = String.valueOf(auth.getPrincipal()).trim();
        if ("sandbox-token".equalsIgnoreCase(principal)) {
            return null;
        }
        if ("api-key".equalsIgnoreCase(principal)) {
            if (auth.getDetails() instanceof GatewayAuthDetails d && d.ownerUserId() != null) {
                return d.ownerUserId();
            }
            return null;
        }
        try {
            return Long.valueOf(principal);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
