package com.ninsky.cronos.account.profile.domain;

import com.ninsky.cronos.account.avatar.domain.AvatarKey;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.SequencedMap;
import java.util.Set;
import java.util.UUID;

/**
 * Read model of "my account" as the account-settings screens see it: the {@code users} row plus
 * the personal fields that live on {@code user_profiles}. {@code phoneNumber} stays a raw string
 * because legacy rows the E.164 migration could not parse must still load.
 */
public record UserAccount(
        UUID id,
        String username,
        String email,
        String firstName,
        String lastName,
        String phoneNumber,
        AvatarKey avatarKey,
        boolean enabled,
        boolean accountNonLocked,
        boolean twoFactorEnabled,
        int failedLoginAttempts,
        LocalDateTime lockedUntil,
        LocalDateTime lastLoginAt,
        LocalDateTime passwordChangedAt,
        Set<String> roles,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        long version
) {
    public UserAccount {
        Objects.requireNonNull(id, "id");
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    /** JSON-path-keyed view of the user-editable fields, for audit diffs. */
    public SequencedMap<String, Object> editableSnapshot() {
        SequencedMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("username", username);
        snapshot.put("firstName", firstName);
        snapshot.put("lastName", lastName);
        snapshot.put("phoneNumber", phoneNumber);
        return snapshot;
    }
}
