package com.ninsky.cronos.account.shared.domain;

import java.util.List;

/**
 * Every business failure the account module can produce. Sealed and mapped to HTTP by an
 * exhaustive {@code switch} with no {@code default} ({@code AccountErrorMapper}), so adding a
 * variant is a compile error until it has a status and an {@code errors[]} shape.
 * <p>
 * {@code field} is the request-body JSON path the frontend passes to {@code form.get(field)}
 * ({@code "address.zipCode"}), or null for errors that are not about one input.
 */
public sealed interface AccountDomainError {

    String VALIDATION_ERROR = "VALIDATION_ERROR";
    String VALIDATION_FIELD_ERROR = "VALIDATION_FIELD_ERROR";
    String DUPLICATE_RESOURCE = "DUPLICATE_RESOURCE";
    String SYSTEM_RESOURCE_CONFLICT = "SYSTEM_RESOURCE_CONFLICT";

    String field();

    String code();

    String messageKey();

    List<Object> args();

    /** 409 — username already used by another account (case-insensitive). */
    record DuplicateUsername(String field, String code, String messageKey, List<Object> args) implements AccountDomainError {
        public DuplicateUsername {
            args = List.copyOf(args);
        }

        public static DuplicateUsername of(String username) {
            return new DuplicateUsername("username", DUPLICATE_RESOURCE, "account.profile.username.duplicate", List.of(username));
        }
    }

    /** 400 — one input failed a business/domain rule not expressible as a Bean Validation constraint. */
    record InvalidField(String field, String code, String messageKey, List<Object> args) implements AccountDomainError {
        public InvalidField {
            args = List.copyOf(args);
        }

        public static InvalidField of(String field, String messageKey, Object... args) {
            return new InvalidField(field, VALIDATION_FIELD_ERROR, messageKey, List.of(args));
        }
    }

    /** 400 — SAT regime exists but is not available to this RFC's taxpayer type. */
    record RegimeNotApplicable(String field, String code, String messageKey, List<Object> args) implements AccountDomainError {
        public RegimeNotApplicable {
            args = List.copyOf(args);
        }

        public static RegimeNotApplicable of(String regimeCode, String taxpayerType) {
            return new RegimeNotApplicable("taxRegime", VALIDATION_FIELD_ERROR, "account.fiscal.taxRegime.notApplicable",
                    List.of(regimeCode, taxpayerType));
        }
    }

    /** 400 / 413 / 415 — uploaded avatar is unusable; {@link Reason} picks the status. */
    record ImageRejected(String field, String code, String messageKey, List<Object> args, Reason reason) implements AccountDomainError {
        public enum Reason { INVALID, TOO_LARGE, UNSUPPORTED_TYPE }

        public ImageRejected {
            args = List.copyOf(args);
        }

        public static ImageRejected of(Reason reason, String messageKey, Object... args) {
            return new ImageRejected("file", VALIDATION_FIELD_ERROR, messageKey, List.of(args), reason);
        }
    }

    /** 429 — more than N avatar uploads in the window. */
    record UploadRateLimited(String field, String code, String messageKey, List<Object> args, long retryAfterSeconds) implements AccountDomainError {
        public UploadRateLimited {
            args = List.copyOf(args);
        }

        public static UploadRateLimited of(long retryAfterSeconds) {
            return new UploadRateLimited(null, VALIDATION_ERROR, "account.rateLimit.avatar", List.of(retryAfterSeconds), retryAfterSeconds);
        }
    }

    /** 429 — too many profile / fiscal writes in the window. */
    record WriteRateLimited(String field, String code, String messageKey, List<Object> args, long retryAfterSeconds) implements AccountDomainError {
        public WriteRateLimited {
            args = List.copyOf(args);
        }

        public static WriteRateLimited of(long retryAfterSeconds) {
            return new WriteRateLimited(null, VALIDATION_ERROR, "account.rateLimit.write", List.of(retryAfterSeconds), retryAfterSeconds);
        }
    }

    /** 412 — the client's {@code If-Match} no longer names the current version. */
    record VersionMismatch(String field, String code, String messageKey, List<Object> args) implements AccountDomainError {
        public VersionMismatch {
            args = List.copyOf(args);
        }

        public static VersionMismatch of() {
            return new VersionMismatch(null, SYSTEM_RESOURCE_CONFLICT, "account.concurrency.preconditionFailed", List.of());
        }
    }
}
