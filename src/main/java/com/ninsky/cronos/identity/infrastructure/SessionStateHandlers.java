package com.ninsky.cronos.identity.infrastructure;

import com.ninsky.cronos.eventing.application.OutboxHandler;
import com.ninsky.cronos.eventing.application.OutboxHandlers;
import com.ninsky.cronos.identity.domain.IdentityEvents;
import com.ninsky.cronos.infrastructure.security.session.SessionStateStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Mirrors session lifecycle events into the Redis session state read by the authentication filter on every request
 * (performance-latency P0-4). Writes are absolute ({@code SET}), so redelivery is harmless; lock and unlock of one
 * session share its aggregate id and are therefore applied in order.
 */
@Configuration
public class SessionStateHandlers {

    private final SessionStateStore sessionState;

    public SessionStateHandlers(SessionStateStore sessionState) {
        this.sessionState = sessionState;
    }

    /** The screen lock takes effect on the next request of every access token of the session. */
    @Bean
    OutboxHandler<IdentityEvents.SessionLocked> sessionLockedState() {
        return OutboxHandlers.of(IdentityEvents.SessionLocked.class, "SessionLockedState",
                (event, envelope) -> sessionState.markLocked(event.sessionId()));
    }

    /** Unlocking with the password re-opens the session. */
    @Bean
    OutboxHandler<IdentityEvents.SessionUnlocked> sessionUnlockedState() {
        return OutboxHandlers.of(IdentityEvents.SessionUnlocked.class, "SessionUnlockedState",
                (event, envelope) -> sessionState.markActive(event.sessionId()));
    }

    /**
     * A single ended session is marked revoked; "every session" revocations already set the user cutoff
     * ({@code blacklist:user:{uid}}) synchronously.
     */
    @Bean
    OutboxHandler<IdentityEvents.SessionRevoked> sessionRevokedState() {
        return OutboxHandlers.of(IdentityEvents.SessionRevoked.class, "SessionRevokedState", (event, envelope) -> {
            if (event.sessionId() != null) {
                sessionState.markRevoked(event.sessionId());
            }
        });
    }
}
