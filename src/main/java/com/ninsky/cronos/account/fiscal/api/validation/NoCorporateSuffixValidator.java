package com.ninsky.cronos.account.fiscal.api.validation;

import com.ninsky.cronos.account.fiscal.domain.LegalName;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class NoCorporateSuffixValidator implements ConstraintValidator<NoCorporateSuffix, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || !LegalName.hasCorporateSuffix(value);
    }
}
