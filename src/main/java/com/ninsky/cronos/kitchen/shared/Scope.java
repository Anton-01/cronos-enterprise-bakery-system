package com.ninsky.cronos.kitchen.shared;

import java.util.UUID;

/** SYSTEM = platform catalog (no owner); USER = owned by the caller's tenant. */
public enum Scope {
    SYSTEM,
    USER;

    public static Scope of(UUID ownerId) {
        return ownerId == null ? SYSTEM : USER;
    }
}
