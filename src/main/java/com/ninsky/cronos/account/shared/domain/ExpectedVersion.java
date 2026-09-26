package com.ninsky.cronos.account.shared.domain;

import java.util.OptionalLong;

/**
 * Optimistic-concurrency precondition carried by a write command: either "any version" (no
 * {@code If-Match} header sent) or one specific entity version.
 */
public record ExpectedVersion(OptionalLong version) {

    public static final ExpectedVersion ANY = new ExpectedVersion(OptionalLong.empty());

    public static ExpectedVersion of(long version) {
        return new ExpectedVersion(OptionalLong.of(version));
    }

    /** @param current the persisted version, or null when the resource does not exist yet */
    public boolean matches(Long current) {
        if (version.isEmpty()) {
            return true;
        }
        return current != null && current == version.getAsLong();
    }
}
