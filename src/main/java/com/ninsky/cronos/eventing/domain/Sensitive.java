package com.ninsky.cronos.eventing.domain;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a record component of a {@link DomainEvent} that holds a secret (a raw token, a link carrying one).
 * The outbox stores it encrypted with the column encryption key and the event trail never returns it.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.RECORD_COMPONENT, ElementType.FIELD, ElementType.METHOD})
public @interface Sensitive {
}
