package com.ninsky.cronos.finance.shared;

import java.util.List;

/** A domain-rule failure on a request field: i18n key plus MessageFormat arguments. */
public record FieldIssue(String field, String messageKey, List<Object> args) {

    public FieldIssue(String field, String messageKey, Object... args) {
        this(field, messageKey, List.of(args));
    }

    public FieldIssue {
        args = List.copyOf(args);
    }
}
