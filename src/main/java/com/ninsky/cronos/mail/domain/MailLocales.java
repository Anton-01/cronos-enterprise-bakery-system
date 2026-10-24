package com.ninsky.cronos.mail.domain;

import java.util.List;
import java.util.Locale;

/** The two locales of the catalog. Any English tag maps to {@code en}; everything else to {@code es-MX}. */
public final class MailLocales {

    public static final String SPANISH = "es-MX";
    public static final String ENGLISH = "en";
    public static final List<String> ALL = List.of(SPANISH, ENGLISH);
    /** Keys of a template's {@code content}: the language only. */
    public static final String SPANISH_CONTENT = "es";
    public static final List<String> CONTENT_KEYS = List.of(SPANISH_CONTENT, ENGLISH);

    private MailLocales() {
    }

    /** {@code en}, {@code en-US}… → {@code en}; null and anything else → {@code es-MX}. */
    public static String normalize(String tag) {
        return tag != null && tag.toLowerCase(Locale.ROOT).startsWith("en") ? ENGLISH : SPANISH;
    }

    /** The {@code content} key of {@code tag}: {@code en} for any English tag, {@code es} otherwise. */
    public static String contentKey(String tag) {
        return ENGLISH.equals(normalize(tag)) ? ENGLISH : SPANISH_CONTENT;
    }

    /** The {@link Locale} used for formatting. */
    public static Locale toLocale(String tag) {
        return ENGLISH.equals(normalize(tag)) ? Locale.ENGLISH : Locale.forLanguageTag(SPANISH);
    }
}
