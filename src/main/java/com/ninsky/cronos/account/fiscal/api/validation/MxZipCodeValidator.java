package com.ninsky.cronos.account.fiscal.api.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class MxZipCodeValidator implements ConstraintValidator<MxZipCode, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || value.isBlank() || com.ninsky.cronos.account.fiscal.domain.MxZipCode.isValid(value);
    }
}
