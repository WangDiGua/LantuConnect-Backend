package com.lantu.connect.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = ResourceCodeValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ResourceCode {
    String message() default "resourceCode 仅支持字母/数字/中划线/下划线，长度 3-64";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

