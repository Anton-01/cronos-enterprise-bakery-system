package com.ninsky.cronos.iam.user;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Account lifecycle (spec §3.3). */
public enum UserStatus {
    PENDING_ACTIVATION,
    ACTIVE,
    SUSPENDED,
    LOCKED,
    DEACTIVATED;

    private static final Map<UserStatus, Set<UserStatus>> TRANSITIONS = Map.of(
            PENDING_ACTIVATION, EnumSet.of(ACTIVE, DEACTIVATED),
            ACTIVE, EnumSet.of(SUSPENDED, LOCKED, DEACTIVATED),
            SUSPENDED, EnumSet.of(ACTIVE, DEACTIVATED),
            LOCKED, EnumSet.of(ACTIVE, DEACTIVATED),
            DEACTIVATED, EnumSet.of(ACTIVE));

    public boolean canMoveTo(UserStatus target) {
        return TRANSITIONS.get(this).contains(target);
    }

    /** {@code until} is only meaningful for temporary states. */
    public boolean acceptsUntil() {
        return this == SUSPENDED || this == LOCKED;
    }
}
