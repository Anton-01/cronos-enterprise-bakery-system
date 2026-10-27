package com.ninsky.cronos.mail.domain;

import java.util.Locale;

/**
 * Request-locale helpers for senders. Any English tag maps to {@code en}; everything else to {@code es-MX}.
 *
 * @see EmailLocale
 */
public final class MailLocales {

    public static final String SPANISH = EmailLocale.ES_MX.tag();
    public static final String ENGLISH = EmailLocale.EN.tag();

    private MailLocales() {
    }

    /** {@code en}, {@code en-US}… → {@code en}; null and anything else → {@code es-MX}. */
    public static String normalize(String tag) {
        return EmailLocale.normalize(tag).tag();
    }

    /** The {@link Locale} used for formatting. */
    public static Locale toLocale(String tag) {
        return EmailLocale.normalize(tag).locale();
    }
}
