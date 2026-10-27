package com.ninsky.cronos.mail.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/** Building blocks of a catalog email; each has one HTML fragment and one plain-text form (the {@code type} JSON value). */
public enum BlockType {
    HEADING,
    PARAGRAPH,
    BUTTON,
    CODE,
    DETAILS,
    CALLOUT,
    LIST,
    IMAGE,
    DIVIDER,
    SIGNATURE;

    @JsonValue
    public String json() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * @throws IllegalArgumentException for an unknown block type (the catalog fails to load)
     */
    @JsonCreator
    public static BlockType of(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
