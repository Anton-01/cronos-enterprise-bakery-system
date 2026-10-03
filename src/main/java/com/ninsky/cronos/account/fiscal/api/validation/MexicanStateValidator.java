package com.ninsky.cronos.account.fiscal.api.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class MexicanStateValidator implements ConstraintValidator<MexicanState, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || value.isBlank() || com.ninsky.cronos.account.fiscal.domain.MexicanState.fromCode(value).isPresent();
    }
}
