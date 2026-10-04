package com.ninsky.cronos.infrastructure.exception;

import java.util.List;

/**
 * A business-rule failure carrying an i18n message key instead of a hard-coded sentence, so
 * {@link GlobalExceptionHandler} answers in the caller's language (es/en) and the .xlsx import
 * reports the very same wording for the very same rule.
 */
public class CatalogException extends RuntimeException {

    /** Maps onto an {@link ErrorCodes} catalog row and its HTTP status. */
    public enum Reason {
        NOT_FOUND(ErrorCodes.RESOURCE_NOT_FOUND),
        DUPLICATE(ErrorCodes.DUPLICATE_RESOURCE),
        INTEGRITY(ErrorCodes.DATA_INTEGRITY_VIOLATION),
        BUSINESS_RULE(ErrorCodes.BUSINESS_CONFLICT),
        INVALID(ErrorCodes.VALIDATION_FAILED);

        private final String errorCode;

        Reason(String errorCode) {
            this.errorCode = errorCode;
        }

        public String errorCode() {
            return errorCode;
        }
    }

    private final Reason reason;
    private final String messageKey;
    private final transient List<Object> args;
    private final String field;

    public CatalogException(Reason reason, String messageKey, String field, Object... args) {
        super(messageKey);
        this.reason = reason;
        this.messageKey = messageKey;
        this.field = field;
        this.args = List.of(args);
    }

    public static CatalogException notFound(String messageKey, Object... args) {
        return new CatalogException(Reason.NOT_FOUND, messageKey, null, args);
    }

    public static CatalogException duplicate(String field, String messageKey, Object... args) {
        return new CatalogException(Reason.DUPLICATE, messageKey, field, args);
    }

    public static CatalogException integrity(String messageKey, Object... args) {
        return new CatalogException(Reason.INTEGRITY, messageKey, null, args);
    }

    public static CatalogException businessRule(String messageKey, Object... args) {
        return new CatalogException(Reason.BUSINESS_RULE, messageKey, null, args);
    }

    public static CatalogException invalid(String field, String messageKey, Object... args) {
        return new CatalogException(Reason.INVALID, messageKey, field, args);
    }

    public Reason reason() {
        return reason;
    }

    public String messageKey() {
        return messageKey;
    }

    public Object[] args() {
        return args.toArray();
    }

    /** Request-body field the failure belongs to, or {@code null} when it isn't tied to one input. */
    public String field() {
        return field;
    }
}
