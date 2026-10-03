package com.ninsky.cronos.application.service.catalog;

import com.ninsky.cronos.infrastructure.exception.CatalogException;

import java.util.List;

/**
 * One broken catalog rule: which input field, which i18n message (and its arguments), and what kind
 * of failure it is. The REST path throws the first one as a {@link CatalogException}; the import
 * path collects all of them as row issues.
 */
public record RuleViolation(CatalogException.Reason reason, String field, String messageKey, List<Object> args) {

    public RuleViolation {
        args = List.copyOf(args);
    }

    public static RuleViolation of(CatalogException.Reason reason, String field, String messageKey, Object... args) {
        return new RuleViolation(reason, field, messageKey, List.of(args));
    }

    /** REST semantics: a single-record write fails on its first broken rule. */
    public static void throwFirst(List<RuleViolation> violations) {
        if (!violations.isEmpty()) {
            throw violations.getFirst().toException();
        }
    }

    public CatalogException toException() {
        return new CatalogException(reason, messageKey, field, args.toArray());
    }
}
