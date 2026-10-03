package com.ninsky.cronos.account.fiscal.api.validation;

import com.ninsky.cronos.account.fiscal.domain.TaxRegime;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class SatTaxRegimeValidator implements ConstraintValidator<SatTaxRegime, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || value.isBlank() || TaxRegime.fromCode(value).isPresent();
    }
}
