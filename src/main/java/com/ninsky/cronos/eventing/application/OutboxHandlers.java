package com.ninsky.cronos.eventing.application;

import com.ninsky.cronos.eventing.domain.DomainEvent;

import java.util.Objects;
import java.util.function.BiConsumer;

/** Builds small named {@link OutboxHandler}s from lambdas (declared as {@code @Bean}s next to their collaborators). */
public final class OutboxHandlers {

    private OutboxHandlers() {
    }

    /**
     * A handler of {@code type} named {@code name} (the stable {@code processed_events} key).
     */
    public static <E extends DomainEvent> OutboxHandler<E> of(Class<E> type, String name, BiConsumer<E, EventEnvelope> body) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(body, "body");
        return new OutboxHandler<>() {
            @Override
            public Class<E> eventClass() {
                return type;
            }

            @Override
            public void handle(E event, EventEnvelope envelope) {
                body.accept(event, envelope);
            }

            @Override
            public String name() {
                return name;
            }
        };
    }
}
