package com.ninsky.cronos.account.fiscal.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Class-level: the tax regime must apply to the taxpayer type the RFC encodes (13 chars → persona física, 12 → moral). Reported on property {@code taxRegime}. */
@Documented
@Constraint(validatedBy = RegimeMatchesRfcValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RegimeMatchesRfc {

    String message() default "{account.fiscal.taxRegime.notApplicableToRfc}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
