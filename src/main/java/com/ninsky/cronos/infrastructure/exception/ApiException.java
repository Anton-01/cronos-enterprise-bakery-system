package com.ninsky.cronos.infrastructure.exception;

import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A business failure of the IAM/Finance contract: one or more {@link Violation}s, each with a stable
 * code, the request-body {@code field} path (or null) and an i18n message key. The HTTP status is
 * the first violation's.
 */
public class ApiException extends RuntimeException {

    private final transient List<Violation> violations;
    private final Long retryAfterSeconds;
    private final HttpStatus statusOverride;

    public ApiException(List<Violation> violations, Long retryAfterSeconds) {
        this(violations, retryAfterSeconds, null);
    }

    /** {@code statusOverride} replaces the first code's status (a code whose status depends on the endpoint). */
    public ApiException(List<Violation> violations, Long retryAfterSeconds, HttpStatus statusOverride) {
        super(violations.isEmpty() ? "ApiException" : violations.getFirst().code() + ": " + violations.getFirst().messageKey());
        if (violations.isEmpty()) {
            throw new IllegalArgumentException("At least one violation is required");
        }
        this.violations = List.copyOf(violations);
        this.retryAfterSeconds = retryAfterSeconds;
        this.statusOverride = statusOverride;
    }

    public static ApiException of(ApiErrorCode code, String field, String messageKey, Object... args) {
        return new ApiException(List.of(new Violation(code, field, messageKey, List.of(args))), null);
    }

    /** One violation carrying structured {@code details} for the client (envelope {@code errors[0].details}). */
    public static ApiException withDetails(ApiErrorCode code, String field, Map<String, Object> details, String messageKey, Object... args) {
        return new ApiException(List.of(new Violation(code, field, messageKey, List.of(args), details)), null);
    }

    public static ApiException notFound(String messageKey, Object... args) {
        return of(ApiErrorCode.RESOURCE_NOT_FOUND, null, messageKey, args);
    }

    public static ApiException invalid(String field, String messageKey, Object... args) {
        return of(ApiErrorCode.VALIDATION_ERROR, field, messageKey, args);
    }

    public static ApiException concurrentModification() {
        return of(ApiErrorCode.CONCURRENT_MODIFICATION, "version", "api.concurrentModification");
    }

    public static ApiException rateLimited(long retryAfterSeconds) {
        return new ApiException(List.of(new Violation(ApiErrorCode.RATE_LIMITED, null, "api.rateLimited", List.of(retryAfterSeconds))),
                retryAfterSeconds);
    }

    public List<Violation> violations() {
        return violations;
    }

    public ApiErrorCode primaryCode() {
        return violations.getFirst().code();
    }

    public Long retryAfterSeconds() {
        return retryAfterSeconds;
    }

    /** HTTP status of the response: the override, else the first code's. */
    public HttpStatus status() {
        return statusOverride != null ? statusOverride : primaryCode().status();
    }

    /** Same violations answered with {@code status}. */
    public ApiException withStatus(HttpStatus status) {
        return new ApiException(violations, retryAfterSeconds, status);
    }

    /** RATE_LIMITED carrying {@code details.retryAfterSeconds} as well as the header. */
    public static ApiException rateLimitedWithDetails(long retryAfterSeconds) {
        return new ApiException(List.of(new Violation(ApiErrorCode.RATE_LIMITED, null, "api.rateLimited", List.of(retryAfterSeconds),
                Map.of("retryAfterSeconds", retryAfterSeconds))), retryAfterSeconds);
    }

    /** One error entry of the envelope; {@code details} is optional structured context. */
    public record Violation(ApiErrorCode code, String field, String messageKey, List<Object> args, Map<String, Object> details) {
        public Violation {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(messageKey, "messageKey");
            args = args == null ? List.of() : List.copyOf(args.stream().map(a -> a == null ? "" : a).toList());
            details = details == null || details.isEmpty() ? null : Map.copyOf(details);
        }

        public Violation(ApiErrorCode code, String field, String messageKey, List<Object> args) {
            this(code, field, messageKey, args, null);
        }
    }
}
