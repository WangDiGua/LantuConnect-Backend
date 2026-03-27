package com.lantu.connect.common.security;

import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;

@Aspect
@Component
@Order(50)
@RequiredArgsConstructor
public class RequireRoleAspect {

    private final CasbinAuthorizationService casbinAuthorizationService;

    @Before("execution(* com.lantu.connect..controller..*(..))")
    public void checkController(JoinPoint jp) {
        MethodSignature sig = (MethodSignature) jp.getSignature();
        Method method = sig.getMethod();
        RequireRole ann = method.getAnnotation(RequireRole.class);
        if (ann == null) {
            ann = method.getDeclaringClass().getAnnotation(RequireRole.class);
        }
        if (ann == null) {
            return;
        }

        Long userId = resolveAuthenticatedUserId();

        boolean ok = casbinAuthorizationService.hasAnyRole(userId, ann.value());
        if (!ok) {
            throw new BusinessException(ResultCode.FORBIDDEN, "权限不足，需要角色: " + String.join(", ", Arrays.asList(ann.value())));
        }
    }

    private static Long resolveAuthenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal() == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未认证");
        }
        try {
            return Long.valueOf(String.valueOf(authentication.getPrincipal()));
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户无效");
        }
    }
}
