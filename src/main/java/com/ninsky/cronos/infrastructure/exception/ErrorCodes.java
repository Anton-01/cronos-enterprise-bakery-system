package com.ninsky.cronos.infrastructure.exception;

/**
 * Error codes shared between {@link GlobalExceptionHandler} (HTTP response construction) and
 * {@link com.ninsky.cronos.infrastructure.aop.ErrorInterceptorAspect} (MDC categorization).
 * Each constant must match a seeded {@code catalog_statuses.code} row
 * (see {@code db/migration/V2__error_event_catalogs.sql}) — this is the single place both
 * consult so the two never drift apart.
 */
public final class ErrorCodes {

    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String TYPE_MISMATCH = "TYPE_MISMATCH";
    public static final String RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";
    public static final String USER_NOT_FOUND = "USER_NOT_FOUND";
    public static final String ROUTE_NOT_FOUND = "ROUTE_NOT_FOUND";
    public static final String DUPLICATE_RESOURCE = "DUPLICATE_RESOURCE";
    public static final String SYSTEM_RESOURCE_CONFLICT = "SYSTEM_RESOURCE_CONFLICT";
    public static final String UNAUTHORIZED_MODIFICATION = "UNAUTHORIZED_MODIFICATION";
    public static final String DATA_INTEGRITY_VIOLATION = "DATA_INTEGRITY_VIOLATION";
    public static final String BUSINESS_CONFLICT = "BUSINESS_CONFLICT";
    public static final String INVALID_TOKEN = "INVALID_TOKEN";
    public static final String AUTHENTICATION_FAILED = "AUTHENTICATION_FAILED";
    public static final String RATE_LIMIT_EXCEEDED = "RATE_LIMIT_EXCEEDED";
    public static final String UNEXPECTED_ERROR = "UNEXPECTED_ERROR";

    private ErrorCodes() {
    }
}
