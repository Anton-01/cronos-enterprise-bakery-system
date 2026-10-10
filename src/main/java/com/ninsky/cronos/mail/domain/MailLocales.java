package com.ninsky.cronos.mail.domain;

import java.util.List;
import java.util.Locale;

/** The two locales of the catalog. Any English tag maps to {@code en}; everything else to {@code es-MX}. */
public final class MailLocales {

    public static final String SPANISH = "es-MX";
    public static final String ENGLISH = "en";
    public static final List<String> ALL = List.of(SPANISH, ENGLISH);

    private MailLocales() {
    }

    /** {@code en}, {@code en-US}… → {@code en}; null and anything else → {@code es-MX}. */
    public static String normalize(String tag) {
        return tag != null && tag.toLowerCase(Locale.ROOT).startsWith("en") ? ENGLISH : SPANISH;
    }

    /** The {@link Locale} used for formatting. */
    public static Locale toLocale(String tag) {
        return ENGLISH.equals(normalize(tag)) ? Locale.ENGLISH : Locale.forLanguageTag(SPANISH);
    }
}
