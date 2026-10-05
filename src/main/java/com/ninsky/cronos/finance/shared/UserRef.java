package com.ninsky.cronos.finance.shared;

import java.util.UUID;

/** Who last touched a row (spec §2): {@code displayName} = trim(first + last), else username. */
public record UserRef(UUID id, String username, String displayName, String avatarUrl) {
}
