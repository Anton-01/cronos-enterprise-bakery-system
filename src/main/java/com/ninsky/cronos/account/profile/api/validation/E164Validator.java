package com.ninsky.cronos.account.profile.api.validation;

import com.ninsky.cronos.account.profile.domain.E164Phone;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class E164Validator implements ConstraintValidator<E164, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || E164Phone.isValid(value);
    }
}
