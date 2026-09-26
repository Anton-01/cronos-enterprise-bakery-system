package com.ninsky.cronos.account.profile.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** E.164 shape ({@code ^\+[1-9]\d{6,14}$}) AND a real number per libphonenumber. {@code null} is valid (clears). */
@Documented
@Constraint(validatedBy = E164Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface E164 {

    String message() default "{account.profile.phoneNumber.invalid}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
