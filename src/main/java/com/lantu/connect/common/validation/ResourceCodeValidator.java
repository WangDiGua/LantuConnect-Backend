package com.lantu.connect.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

public class ResourceCodeValidator implements ConstraintValidator<ResourceCode, String> {

    private static final Pattern P = Pattern.compile("^[a-zA-Z0-9_-]{3,64}$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return P.matcher(value.trim()).matches();
    }
}

