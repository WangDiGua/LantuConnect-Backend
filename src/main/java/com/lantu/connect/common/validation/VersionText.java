package com.lantu.connect.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = VersionTextValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface VersionText {
    String message() default "version 格式非法，示例: v1.0.0 或 1.0.0-beta";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

