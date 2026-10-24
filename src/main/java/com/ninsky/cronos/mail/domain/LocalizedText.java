package com.ninsky.cronos.mail.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

/** A short catalog text in both locales, serialised as {@code { "es-MX": …, "en": … }}. */
public record LocalizedText(@JsonProperty("es-MX") String esMx, @JsonProperty("en") String en) {

    /** The text of {@code locale}. */
    public String of(EmailLocale locale) {
        return locale == EmailLocale.EN ? en : esMx;
    }

    /** True when both locales have a non-blank text. */
    public boolean complete() {
        return esMx != null && !esMx.isBlank() && en != null && !en.isBlank();
    }
}
