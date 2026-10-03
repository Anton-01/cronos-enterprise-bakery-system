package com.ninsky.cronos.account.shared.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Request DTO opt-in: any JSON property that is not a component of the record is a 400
 * ({@code VALIDATION_ERROR}, {@code field} = the offending property), never silently ignored.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RejectUnknownProperties {
}
