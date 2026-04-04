package com.lantu.connect.common.util;

import com.lantu.connect.common.security.CasbinAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
@RequiredArgsConstructor
public class DeptScopeHelper {

    private final CasbinAuthorizationService casbinAuthorizationService;

    public Long getCurrentUserMenuId() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return null;
        }
        return casbinAuthorizationService.userDepartmentMenuId(userId);
    }

    public Long getCurrentUserId() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            String uid = sra.getRequest().getHeader("X-User-Id");
            if (uid != null && !uid.isBlank()) {
                try {
                    return Long.valueOf(uid.trim());
                } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }
}
