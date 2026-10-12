package com.ninsky.cronos.mail.domain;

import java.util.Objects;

/**
 * A variable a template declares; senders must supply every declared variable.
 *
 * @param sample value used by previews and by the "every template renders" test
 */
public record TemplateVariable(String name, String description, String sample, VariableKind kind) {

    public TemplateVariable {
        Objects.requireNonNull(name, "name");
        kind = kind == null ? VariableKind.TEXT : kind;
        sample = sample == null ? "" : sample;
    }
}
