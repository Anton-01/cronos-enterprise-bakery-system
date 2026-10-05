package com.ninsky.cronos.infrastructure.exception;

import org.springframework.http.HttpStatus;

/** Stable error codes of the IAM/Finance contract (spec §1.3). Never localised. */
public enum ApiErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT),
    RESOURCE_IN_USE(HttpStatus.CONFLICT),
    INVALID_STATE_TRANSITION(HttpStatus.CONFLICT),
    SELF_MODIFICATION_FORBIDDEN(HttpStatus.FORBIDDEN),
    PRIVILEGE_ESCALATION(HttpStatus.FORBIDDEN),
    SOD_CONFLICT(HttpStatus.CONFLICT),
    SYSTEM_RESOURCE_CONFLICT(HttpStatus.CONFLICT),
    DEFAULT_LOCKED(HttpStatus.CONFLICT),
    ACCESS_DENIED(HttpStatus.FORBIDDEN),
    TWO_FACTOR_ENROLLMENT_REQUIRED(HttpStatus.FORBIDDEN),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ApiErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    /** Bundle key of the envelope's top-level message for this code. */
    public String titleKey() {
        return "api.error." + name();
    }
}
