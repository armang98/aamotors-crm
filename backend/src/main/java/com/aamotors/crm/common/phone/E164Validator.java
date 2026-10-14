package com.aamotors.crm.common.phone;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class E164Validator implements ConstraintValidator<E164, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true; // use @NotBlank for presence
        }
        return PhoneNumberNormalizer.isValid(value);
    }
}
