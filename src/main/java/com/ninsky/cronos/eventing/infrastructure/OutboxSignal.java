package com.ninsky.cronos.eventing.infrastructure;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * "Something was committed to the outbox" notification from publishers to relays, without a bean cycle
 * (relay → handlers → publisher → relay). Relays subscribe once; publishers signal after commit.
 */
@Component
public class OutboxSignal {

    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    /** Registers {@code listener}; it must return quickly (hand work to its own executor). */
    public void subscribe(Runnable listener) {
        listeners.add(listener);
    }

    /** Notifies every listener; never throws. */
    public void committed() {
        listeners.forEach(listener -> {
            try {
                listener.run();
            } catch (RuntimeException ignored) {
                // A relay that cannot be woken will pick the message up on its next poll.
            }
        });
    }
}
