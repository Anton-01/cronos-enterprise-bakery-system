package com.ninsky.cronos.application.imports;

/**
 * One finding. {@code row} is the 1-based worksheet row as the user sees it in Excel (header = 1),
 * {@code null} for file-level findings; {@code column} is the header name, {@code null} when not
 * tied to a cell. {@code code} is the stable i18n key (machine-readable); {@code message} is that
 * key resolved in the requester's language.
 */
public record ImportIssue(Integer row, String column, Severity severity, String code, String message) {

    public enum Severity {
        /** Blocks the whole import. */
        ERROR,
        /** Informational; never blocks. */
        WARNING
    }
}
