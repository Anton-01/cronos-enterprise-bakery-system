package com.ninsky.cronos.iam.token;

import java.time.Instant;

/** A freshly issued token: the raw value only travels to the mailer, never to a response or log. */
public record IssuedToken(String rawToken, Instant expiresAt) {

    @Override
    public String toString() {
        return "IssuedToken[expiresAt=" + expiresAt + "]";
    }
}
