package com.ninsky.cronos.finance.shared;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Runs {@link SelfValidating#ruleIssues()} and reports each issue on its own field path. */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DomainRules.Validator.class)
public @interface DomainRules {

    String message() default "{api.validation.invalidValue}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<DomainRules, SelfValidating> {

        @Override
        public boolean isValid(SelfValidating value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            var issues = value.ruleIssues();
            if (issues.isEmpty()) {
                return true;
            }
            context.disableDefaultConstraintViolation();
            issues.forEach(issue -> context.buildConstraintViolationWithTemplate("{" + issue.messageKey() + "}")
                    .addPropertyNode(issue.field())
                    .addConstraintViolation());
            return false;
        }
    }
}
