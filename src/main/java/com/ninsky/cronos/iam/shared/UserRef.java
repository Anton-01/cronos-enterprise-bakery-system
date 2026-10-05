package com.ninsky.cronos.iam.shared;

import java.util.UUID;

/** Compact user reference (spec §2). */
public record UserRef(UUID id, String username, String displayName, String avatarUrl) {

    /** {@code trim(first + " " + last)}, falling back to the username. */
    public static String displayName(String firstName, String lastName, String username) {
        String joined = ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
        return joined.isEmpty() ? username : joined;
    }
}
