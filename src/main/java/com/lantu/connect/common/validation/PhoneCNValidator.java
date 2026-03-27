package com.lantu.connect.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

public class PhoneCNValidator implements ConstraintValidator<PhoneCN, String> {

    private static final Pattern P = Pattern.compile("^1[3-9]\\d{9}$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (!StringUtils.hasText(value)) {
            return true;
        }
        return P.matcher(value.trim()).matches();
    }
}

