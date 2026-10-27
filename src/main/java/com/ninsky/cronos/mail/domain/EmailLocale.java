package com.ninsky.cronos.mail.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * The two locales of the email catalog. The JSON value (also the {@code content} and {@code description} keys and the
 * {@code {locale}} path segment) is the tag: {@code es-MX} or {@code en}, the same values as {@code Accept-Language}.
 */
public enum EmailLocale {

    @JsonProperty("es-MX")
    ES_MX("es-MX", Locale.forLanguageTag("es-MX")),

    @JsonProperty("en")
    EN("en", Locale.ENGLISH);

    private final String tag;
    private final Locale locale;

    EmailLocale(String tag, Locale locale) {
        this.tag = tag;
        this.locale = locale;
    }

    /** {@code es-MX} or {@code en}. */
    public String tag() {
        return tag;
    }

    /** The {@link Locale} used for formatting. */
    public Locale locale() {
        return locale;
    }

    /** Exactly {@code es-MX} or {@code en} (case-sensitive, as in the path segment); anything else is empty. */
    public static Optional<EmailLocale> fromTag(String tag) {
        return Arrays.stream(values()).filter(value -> value.tag.equals(tag)).findFirst();
    }

    /** Lenient: {@code en}, {@code en-US}… → {@link #EN}; null and anything else → {@link #ES_MX} (the tenant language). */
    public static EmailLocale normalize(String tag) {
        return tag != null && tag.toLowerCase(Locale.ROOT).startsWith("en") ? EN : ES_MX;
    }

    @Override
    public String toString() {
        return tag;
    }
}
