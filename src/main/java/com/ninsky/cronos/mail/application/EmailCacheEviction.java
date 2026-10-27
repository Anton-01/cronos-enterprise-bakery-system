package com.ninsky.cronos.mail.application;

import com.ninsky.cronos.mail.domain.EmailLocale;

import java.util.Objects;
import java.util.UUID;

/**
 * What a committed write made stale. Published as a Spring event inside the writing transaction (evicted on this
 * instance after commit) and fanned out to every instance by the outbox consumer.
 *
 * @param templateKey null for a settings change (which invalidates every rendered template of the tenant)
 * @param locale      null for a settings change
 */
public record EmailCacheEviction(UUID tenantId, String templateKey, EmailLocale locale) {

    public EmailCacheEviction {
        Objects.requireNonNull(tenantId, "tenantId");
    }

    /** A content save or reset of one template locale. */
    public static EmailCacheEviction content(UUID tenantId, String templateKey, EmailLocale locale) {
        return new EmailCacheEviction(tenantId, Objects.requireNonNull(templateKey), Objects.requireNonNull(locale));
    }

    /** A brand/sender save. */
    public static EmailCacheEviction settings(UUID tenantId) {
        return new EmailCacheEviction(tenantId, null, null);
    }

    public boolean settingsChange() {
        return templateKey == null;
    }
}
