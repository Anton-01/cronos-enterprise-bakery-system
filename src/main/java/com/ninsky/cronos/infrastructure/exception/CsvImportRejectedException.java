package com.ninsky.cronos.infrastructure.exception;

import java.util.List;

/**
 * A CSV import refused as a whole (nothing was written): every problem found, each with its CSV line
 * ({@code null} = file-level), column and i18n message key. {@link GlobalExceptionHandler} answers
 * 400 with one localized error per problem.
 */
public class CsvImportRejectedException extends RuntimeException {

    public record RowError(Integer line, String column, String messageKey, List<Object> args) {
        public RowError {
            args = List.copyOf(args);
        }

        public static RowError of(Integer line, String column, String messageKey, Object... args) {
            return new RowError(line, column, messageKey, List.of(args));
        }
    }

    private final transient List<RowError> errors;

    public CsvImportRejectedException(List<RowError> errors) {
        super("CSV import rejected: " + errors.size() + " error(s)");
        this.errors = List.copyOf(errors);
    }

    public List<RowError> errors() {
        return errors;
    }
}
