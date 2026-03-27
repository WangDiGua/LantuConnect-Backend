package com.lantu.connect.common.annotation;

import java.lang.annotation.*;

/**
 * 审计日志自定义注解
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {
    String action() default "";
    String resource() default "";
}
