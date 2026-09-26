package com.ninsky.cronos.account.fiscal.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** ISO 3166-2:MX subdivision code without prefix (AGU … ZAC, CMX). */
@Documented
@Constraint(validatedBy = MexicanStateValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface MexicanState {

    String message() default "{account.fiscal.state.invalid}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
