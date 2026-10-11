package com.ninsky.cronos.mail.application;

import java.util.UUID;

/**
 * Whether a user still wants a non-transactional email (§6.5: the EMAIL toggle of its category). Transactional
 * templates and test sends never ask.
 */
public interface MailPreferences {

    /** Every email allowed (used when no notification module is present). */
    MailPreferences ALLOW_ALL = (userId, templateKey) -> true;

    boolean allows(UUID userId, String templateKey);
}
