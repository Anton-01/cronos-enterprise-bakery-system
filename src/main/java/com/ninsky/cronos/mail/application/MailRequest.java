package com.ninsky.cronos.mail.application;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * What a sender asks the {@link Mailer} for. Null variable values are sent as empty text (optional facts such as a
 * comment); a variable missing altogether is a programming error.
 *
 * @param dedupeKey coalesces repeated requests (e.g. one digest per 10 minutes), null to always send
 */
public record MailRequest(String templateKey, String locale, String to, Map<String, String> variables, UUID recipientUserId,
                          String dedupeKey) {

    public MailRequest {
        Objects.requireNonNull(templateKey, "templateKey");
        Map<String, String> copy = new HashMap<>();
        if (variables != null) {
            variables.forEach((name, value) -> copy.put(name, value == null ? "" : value));
        }
        variables = Map.copyOf(copy);
    }

    /** A request without account or dedupe key. */
    public static MailRequest of(String templateKey, String locale, String to, Map<String, String> variables) {
        return new MailRequest(templateKey, locale, to, variables, null, null);
    }

    /** Same request addressed to an account holder. */
    public MailRequest forUser(UUID userId) {
        return new MailRequest(templateKey, locale, to, variables, userId, dedupeKey);
    }

    /** Same request, coalesced on {@code key}. */
    public MailRequest dedupedBy(String key) {
        return new MailRequest(templateKey, locale, to, variables, recipientUserId, key);
    }

    @Override
    public String toString() {
        return "MailRequest[templateKey=" + templateKey + "]";
    }
}
