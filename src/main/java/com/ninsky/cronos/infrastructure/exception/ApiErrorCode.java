package com.ninsky.cronos.infrastructure.exception;

import org.springframework.http.HttpStatus;

/** Stable error codes of the IAM/Finance and kitchen contracts. Never localised. */
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
    TWO_FACTOR_ALREADY_ENABLED(HttpStatus.CONFLICT),
    TWO_FACTOR_REQUIRED_BY_ROLE(HttpStatus.CONFLICT),
    ENROLLMENT_EXPIRED(HttpStatus.CONFLICT),
    INVALID_TOTP_CODE(HttpStatus.BAD_REQUEST),
    INVALID_RECOVERY_CODE(HttpStatus.BAD_REQUEST),
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    // Kitchen catalog & recipes (§8)
    UNIT_INCOMPATIBLE(HttpStatus.BAD_REQUEST),
    CURRENCY_NOT_SUPPORTED(HttpStatus.BAD_REQUEST),
    QUOTA_EXCEEDED(HttpStatus.CONFLICT),
    PRICE_BELOW_COST(HttpStatus.BAD_REQUEST),
    INVALID_SUBSTITUTION(HttpStatus.BAD_REQUEST),
    // Authentication (auth-quotes-calendar-notifications §2.9)
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    ACCOUNT_LOCKED(HttpStatus.UNAUTHORIZED),
    ACCOUNT_PENDING_APPROVAL(HttpStatus.FORBIDDEN),
    ACCOUNT_REJECTED(HttpStatus.FORBIDDEN),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN),
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN),
    INVALID_TWO_FACTOR_CODE(HttpStatus.UNAUTHORIZED),
    TWO_FACTOR_CHALLENGE_EXPIRED(HttpStatus.UNAUTHORIZED),
    PASSWORD_POLICY_VIOLATION(HttpStatus.BAD_REQUEST),
    PASSWORD_REUSED(HttpStatus.BAD_REQUEST),
    /** 400 for links (reset, verification); thrown with a 401 override for sessions (unlock). */
    INVALID_TOKEN(HttpStatus.BAD_REQUEST),
    SESSION_LOCKED(HttpStatus.LOCKED),
    // Email template customization (email-template-customization §4)
    EMAIL_TEMPLATE_NOT_FOUND(HttpStatus.NOT_FOUND),
    /** The revision the editor started from is no longer the stored one (or an override appeared meanwhile). */
    STALE_REVISION(HttpStatus.CONFLICT),
    UNKNOWN_VARIABLE(HttpStatus.BAD_REQUEST),
    SENDER_DOMAIN_NOT_VERIFIED(HttpStatus.BAD_REQUEST),
    ASSET_INVALID_TYPE(HttpStatus.BAD_REQUEST),
    ASSET_TOO_LARGE(HttpStatus.BAD_REQUEST),
    ASSET_DIMENSIONS(HttpStatus.BAD_REQUEST),
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
