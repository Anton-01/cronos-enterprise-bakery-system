package com.ninsky.cronos.account.fiscal.api.validation;

import com.ninsky.cronos.account.fiscal.domain.Rfc;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Reports the most specific reason (format / generic / date / check digit), each with its own message. */
public class ValidRfcValidator implements ConstraintValidator<ValidRfc, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return Rfc.check(Rfc.normalize(value))
                .map(violation -> {
                    context.disableDefaultConstraintViolation();
                    context.buildConstraintViolationWithTemplate("{" + violation.messageKey() + "}").addConstraintViolation();
                    return false;
                })
                .orElse(true);
    }
}
