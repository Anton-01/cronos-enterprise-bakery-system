package com.ninsky.cronos.infrastructure.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks controllers that follow the IAM/Finance error contract (spec §1.3): {@code VALIDATION_ERROR}
 * field errors, {@code CONCURRENT_MODIFICATION}, {@code ACCESS_DENIED} and localised titles.
 * Older controllers keep their existing error codes.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface StrictApiContract {
}
