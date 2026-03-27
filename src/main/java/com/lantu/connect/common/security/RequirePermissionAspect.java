package com.lantu.connect.common.security;

import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
@Aspect
@Component
@Order(51)
@RequiredArgsConstructor
@Slf4j
public class RequirePermissionAspect {

    private final CasbinAuthorizationService casbinAuthorizationService;

    @Before("execution(* com.lantu.connect..controller..*(..))")
    public void checkPermission(JoinPoint jp) {
        MethodSignature sig = (MethodSignature) jp.getSignature();
        Method method = sig.getMethod();
        RequirePermission ann = method.getAnnotation(RequirePermission.class);
        if (ann == null) {
            ann = method.getDeclaringClass().getAnnotation(RequirePermission.class);
        }
        if (ann == null) {
            return;
        }

        Long userId = resolveAuthenticatedUserId();

        String[] required = ann.value();
        RequirePermission.LogicalOperator operator = ann.operator();
        boolean granted = casbinAuthorizationService.hasPermissions(userId, required, operator);

        if (!granted) {
            String requiredStr = String.join(", ", required);
            log.warn("权限不足: 用户 {} 缺少权限 [{}]", userId, requiredStr);
            throw new BusinessException(ResultCode.FORBIDDEN, 
                    "权限不足，需要: " + requiredStr);
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
