package com.ninsky.cronos.account.fiscal.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Mexican código postal: 5 digits, zone prefix 01–99. */
@Documented
@Constraint(validatedBy = MxZipCodeValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface MxZipCode {

    String message() default "{account.fiscal.zipCode.format}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
