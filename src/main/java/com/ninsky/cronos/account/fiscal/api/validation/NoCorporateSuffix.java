package com.ninsky.cronos.account.fiscal.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Razón social without the corporate regime suffix (S.A. DE C.V., S. DE R.L., S.A.P.I., S.A.S., S.C., A.C.), as CFDI 4.0 requires. */
@Documented
@Constraint(validatedBy = NoCorporateSuffixValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface NoCorporateSuffix {

    String message() default "{account.fiscal.legalName.corporateSuffix}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
