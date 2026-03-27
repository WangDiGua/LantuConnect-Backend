package com.lantu.connect.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

public class VersionTextValidator implements ConstraintValidator<VersionText, String> {

    private static final Pattern P = Pattern.compile("^v?[0-9]+(\\.[0-9]+){0,3}([-.][a-zA-Z0-9]+)*$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return P.matcher(value.trim()).matches();
    }
}

