package com.ninsky.cronos.account.fiscal.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** A key of SAT catalog c_RegimenFiscal ("601" … "626"). */
@Documented
@Constraint(validatedBy = SatTaxRegimeValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface SatTaxRegime {

    String message() default "{account.fiscal.taxRegime.invalid}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
