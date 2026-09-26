package com.ninsky.cronos.account.profile.domain;

import com.ninsky.cronos.account.shared.domain.DomainValidationException;
import com.ninsky.cronos.account.shared.domain.ExpectedVersion;

import java.util.LinkedHashMap;
import java.util.SequencedMap;

/**
 * Full-replace profile write (PUT semantics): every field is applied, {@code null} clears it.
 * Username is the only required field. Carries no user id: the use case takes identity from
 * {@code CurrentUserProvider} only.
 */
public record ProfileUpdate(
        String username,
        String firstName,
        String lastName,
        E164Phone phoneNumber,
        ExpectedVersion expectedVersion
) {
    public static final int USERNAME_MIN = 3;
    public static final int USERNAME_MAX = 50;
    public static final int NAME_MAX = 100;

    public ProfileUpdate {
        username = blankToNull(username);
        if (username == null || username.length() < USERNAME_MIN || username.length() > USERNAME_MAX) {
            throw new DomainValidationException("account.profile.username.length", USERNAME_MIN, USERNAME_MAX);
        }
        firstName = name(firstName);
        lastName = name(lastName);
        expectedVersion = expectedVersion == null ? ExpectedVersion.ANY : expectedVersion;
    }

    public SequencedMap<String, Object> editableSnapshot() {
        SequencedMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("username", username);
        snapshot.put("firstName", firstName);
        snapshot.put("lastName", lastName);
        snapshot.put("phoneNumber", phoneNumber == null ? null : phoneNumber.value());
        return snapshot;
    }

    private static String name(String raw) {
        String value = blankToNull(raw);
        if (value != null && value.length() > NAME_MAX) {
            throw new DomainValidationException("account.validation.maxLength", "name", NAME_MAX);
        }
        return value;
    }

    public static String blankToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
