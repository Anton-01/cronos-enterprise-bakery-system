package com.ninsky.cronos.account.fiscal.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** RFC: format per taxpayer type, real YYMMDD date, not generic (XAXX/XEXX), valid SAT check digit. {@code null} is left to {@code @NotBlank}. */
@Documented
@Constraint(validatedBy = ValidRfcValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidRfc {

    String message() default "{account.fiscal.taxId.format}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
