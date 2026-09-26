package com.ninsky.cronos.application.request.core.auth.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-level rules for a password change, reported per field so the form can show them inline:
 * {@code newPassword} must differ from {@code currentPassword}; {@code confirmPassword} must equal
 * {@code newPassword}. Both can fail at once.
 */
@Documented
@Constraint(validatedBy = PasswordChangeValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface PasswordChange {

    String message() default "{account.password.invalid}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
