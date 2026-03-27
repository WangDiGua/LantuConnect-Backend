package com.lantu.connect.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 方法级或类级权限要求：当前登录用户（X-User-Id）必须具备指定权限标识。
 * 权限标识格式：资源:操作，如 agent:delete、skill:create
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /**
     * 权限标识数组，如 {"agent:delete", "skill:create"}
     */
    String[] value();

    /**
     * 逻辑操作符：AND 表示需要全部权限，OR 表示需要任一权限
     */
    LogicalOperator operator() default LogicalOperator.OR;

    enum LogicalOperator {
        AND,
        OR
    }
}
