package com.ninsky.cronos.account.shared.api;

import com.ninsky.cronos.account.shared.domain.AccountDomainError;
import com.ninsky.cronos.account.shared.domain.AccountDomainError.DuplicateUsername;
import com.ninsky.cronos.account.shared.domain.AccountDomainError.ImageRejected;
import com.ninsky.cronos.account.shared.domain.AccountDomainError.InvalidField;
import com.ninsky.cronos.account.shared.domain.AccountDomainError.RegimeNotApplicable;
import com.ninsky.cronos.account.shared.domain.AccountDomainError.UploadRateLimited;
import com.ninsky.cronos.account.shared.domain.AccountDomainError.VersionMismatch;
import com.ninsky.cronos.account.shared.domain.AccountDomainError.WriteRateLimited;
import com.ninsky.cronos.account.shared.domain.AccountDomainException;
import com.ninsky.cronos.application.response.envelope.ApiError;
import com.ninsky.cronos.infrastructure.exception.ErrorCodes;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Sealed {@link AccountDomainError} → HTTP. Both switches are exhaustive with no {@code default}:
 * a new error variant does not compile until it is mapped here.
 */
@Component
@RequiredArgsConstructor
public class AccountErrorMapper {

    private final MessageSource messageSource;

    /**
     * @param status        HTTP status
     * @param catalogCode   error-catalog code whose localized title becomes the envelope {@code message}
     * @param errors        de-duplicated (first error per field wins), insertion-ordered
     * @param retryAfterSeconds non-null for 429s
     */
    public record MappedError(HttpStatus status, String catalogCode, List<ApiError> errors, Long retryAfterSeconds) {
        public MappedError {
            errors = List.copyOf(errors);
        }
    }

    public MappedError map(AccountDomainException exception, Locale locale) {
        AccountDomainError primary = exception.primary();
        List<ApiError> errors = List.copyOf(exception.errors().stream()
                .map(error -> toApiError(error, locale))
                .collect(Collectors.toMap(e -> Objects.toString(e.field(), ""), Function.identity(),
                        (first, ignored) -> first, LinkedHashMap::new))
                .sequencedValues());
        return new MappedError(statusOf(primary), catalogCodeOf(primary), errors, retryAfterOf(primary));
    }

    public static HttpStatus statusOf(AccountDomainError error) {
        return switch (error) {
            case DuplicateUsername ignored -> HttpStatus.CONFLICT;
            case InvalidField ignored -> HttpStatus.BAD_REQUEST;
            case RegimeNotApplicable ignored -> HttpStatus.BAD_REQUEST;
            case ImageRejected rejected -> switch (rejected.reason()) {
                case INVALID -> HttpStatus.BAD_REQUEST;
                case TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
                case UNSUPPORTED_TYPE -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            };
            case UploadRateLimited ignored -> HttpStatus.TOO_MANY_REQUESTS;
            case WriteRateLimited ignored -> HttpStatus.TOO_MANY_REQUESTS;
            case VersionMismatch ignored -> HttpStatus.PRECONDITION_FAILED;
        };
    }

    private static String catalogCodeOf(AccountDomainError error) {
        return switch (error) {
            case DuplicateUsername ignored -> ErrorCodes.DUPLICATE_RESOURCE;
            case InvalidField ignored -> ErrorCodes.VALIDATION_FAILED;
            case RegimeNotApplicable ignored -> ErrorCodes.VALIDATION_FAILED;
            case ImageRejected ignored -> ErrorCodes.VALIDATION_FAILED;
            case UploadRateLimited ignored -> ErrorCodes.RATE_LIMIT_EXCEEDED;
            case WriteRateLimited ignored -> ErrorCodes.RATE_LIMIT_EXCEEDED;
            case VersionMismatch ignored -> ErrorCodes.SYSTEM_RESOURCE_CONFLICT;
        };
    }

    private static Long retryAfterOf(AccountDomainError error) {
        return switch (error) {
            case UploadRateLimited limited -> limited.retryAfterSeconds();
            case WriteRateLimited limited -> limited.retryAfterSeconds();
            case DuplicateUsername ignored -> null;
            case InvalidField ignored -> null;
            case RegimeNotApplicable ignored -> null;
            case ImageRejected ignored -> null;
            case VersionMismatch ignored -> null;
        };
    }

    private ApiError toApiError(AccountDomainError error, Locale locale) {
        String message = messageSource.getMessage(error.messageKey(), error.args().toArray(), error.messageKey(), locale);
        return new ApiError(error.code(), message, error.field());
    }
}
