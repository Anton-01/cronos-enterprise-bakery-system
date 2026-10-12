package com.ninsky.cronos.identity.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * What the user typed to sign in, normalised (trim, Unicode NFKC, lower case with {@link Locale#ROOT}).
 *
 * @param email true when it contains {@code @}: looked up by email, otherwise by username
 */
public record LoginIdentifier(String value, boolean email) {

    public LoginIdentifier {
        Objects.requireNonNull(value, "value");
    }

    /**
     * {@code identifier} wins; old clients only send {@code username}. Empty when neither has text.
     */
    public static Optional<LoginIdentifier> resolve(String identifier, String legacyUsername) {
        String raw = identifier != null && !identifier.isBlank() ? identifier : legacyUsername;
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = normalize(raw);
        return normalized.isEmpty() ? Optional.empty() : Optional.of(new LoginIdentifier(normalized, normalized.indexOf('@') >= 0));
    }

    /** trim → NFKC → lower case (ROOT). */
    public static String normalize(String raw) {
        return Normalizer.normalize(raw.trim(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }
}
