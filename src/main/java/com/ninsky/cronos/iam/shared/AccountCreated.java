package com.ninsky.cronos.iam.shared;

import java.util.Objects;
import java.util.UUID;

/**
 * A user account was just created (self sign-up, administration, social login or seeding). Published inside
 * the creating transaction so synchronous listeners (e.g. kitchen defaults) commit or roll back with it.
 */
public record AccountCreated(UUID userId) {

    public AccountCreated {
        Objects.requireNonNull(userId, "userId");
    }
}
