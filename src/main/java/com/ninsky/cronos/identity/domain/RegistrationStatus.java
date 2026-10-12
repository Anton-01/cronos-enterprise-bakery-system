package com.ninsky.cronos.identity.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Life of a self-registration request (§3): only PENDING can be decided or expire. */
public enum RegistrationStatus {
    PENDING,
    APPROVED,
    REJECTED,
    EXPIRED;

    private static final Map<RegistrationStatus, Set<RegistrationStatus>> NEXT = Map.of(
            PENDING, EnumSet.of(APPROVED, REJECTED, EXPIRED),
            APPROVED, EnumSet.noneOf(RegistrationStatus.class),
            REJECTED, EnumSet.noneOf(RegistrationStatus.class),
            EXPIRED, EnumSet.noneOf(RegistrationStatus.class));

    /** True when {@code target} is reachable from this status. */
    public boolean canMoveTo(RegistrationStatus target) {
        return NEXT.get(this).contains(target);
    }
}
