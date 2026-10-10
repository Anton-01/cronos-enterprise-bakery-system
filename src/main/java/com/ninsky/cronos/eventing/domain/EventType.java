package com.ninsky.cronos.eventing.domain;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Stable wire name of a {@link DomainEvent} ({@code context.name}, past tense, e.g. {@code quote.accepted}).
 * The name is stored in the outbox and must never change once released.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface EventType {

    /** The type string. */
    String value();

    /** How failed deliveries of this type are retried before the message is dead-lettered. */
    RetrySchedule retry() default RetrySchedule.DEFAULT;
}
