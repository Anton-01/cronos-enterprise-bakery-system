package com.ninsky.cronos.mail.domain;

import java.util.Map;
import java.util.Objects;

/**
 * Brand and sender of the tenant (singleton per tenant).
 *
 * @param revision optimistic-lock revision; null in a request that starts from the defaults (no row yet)
 */
public record EmailSettings(EmailBrand brand, EmailSender sender, Long revision) {

    public EmailSettings {
        Objects.requireNonNull(brand, "brand");
        Objects.requireNonNull(sender, "sender");
    }

    /** {@code brandName} and {@code supportEmail}: the variables every template may use without declaring them. */
    public Map<String, String> implicitVariables() {
        return Map.of("brandName", nullToEmpty(brand.name()), "supportEmail", nullToEmpty(brand.supportEmail()));
    }

    /** Same settings with another revision. */
    public EmailSettings withRevision(Long newRevision) {
        return new EmailSettings(brand, sender, newRevision);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
