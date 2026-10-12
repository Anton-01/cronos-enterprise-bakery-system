package com.ninsky.cronos.mail.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/** How a template variable is rendered: escaped text, or a link that must use an allowed scheme. */
public enum VariableKind {
    TEXT,
    URL;

    @JsonValue
    public String json() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Case-insensitive; null means text. */
    @JsonCreator
    public static VariableKind of(String value) {
        return value == null ? TEXT : valueOf(value.toUpperCase(Locale.ROOT));
    }
}
