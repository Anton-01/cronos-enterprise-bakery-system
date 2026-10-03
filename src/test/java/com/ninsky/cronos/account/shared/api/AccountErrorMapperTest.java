package com.ninsky.cronos.account.shared.api;

import com.ninsky.cronos.account.shared.domain.AccountDomainError;
import com.ninsky.cronos.account.shared.domain.AccountDomainError.ImageRejected.Reason;
import com.ninsky.cronos.account.shared.domain.AccountDomainException;
import com.ninsky.cronos.application.response.envelope.ApiError;
import com.ninsky.cronos.infrastructure.exception.ErrorCodes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class AccountErrorMapperTest {

    private final StaticMessageSource messages = new StaticMessageSource();
    private final AccountErrorMapper mapper = new AccountErrorMapper(messages);

    static Stream<Arguments> everyVariant() {
        return Stream.of(
                Arguments.of(AccountDomainError.DuplicateUsername.of("admin"), HttpStatus.CONFLICT, ErrorCodes.DUPLICATE_RESOURCE),
                Arguments.of(AccountDomainError.InvalidField.of("address.zipCode", "k"), HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_FAILED),
                Arguments.of(AccountDomainError.RegimeNotApplicable.of("601", "INDIVIDUAL"), HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_FAILED),
                Arguments.of(AccountDomainError.ImageRejected.of(Reason.INVALID, "k"), HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_FAILED),
                Arguments.of(AccountDomainError.ImageRejected.of(Reason.TOO_LARGE, "k"), HttpStatus.PAYLOAD_TOO_LARGE, ErrorCodes.VALIDATION_FAILED),
                Arguments.of(AccountDomainError.ImageRejected.of(Reason.UNSUPPORTED_TYPE, "k"), HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorCodes.VALIDATION_FAILED),
                Arguments.of(AccountDomainError.UploadRateLimited.of(120), HttpStatus.TOO_MANY_REQUESTS, ErrorCodes.RATE_LIMIT_EXCEEDED),
                Arguments.of(AccountDomainError.WriteRateLimited.of(60), HttpStatus.TOO_MANY_REQUESTS, ErrorCodes.RATE_LIMIT_EXCEEDED),
                Arguments.of(AccountDomainError.VersionMismatch.of(), HttpStatus.PRECONDITION_FAILED, ErrorCodes.SYSTEM_RESOURCE_CONFLICT));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("everyVariant")
    void mapsEverySealedVariant(AccountDomainError error, HttpStatus status, String catalogCode) {
        var mapped = mapper.map(new AccountDomainException(error), Locale.ENGLISH);

        assertThat(mapped.status()).isEqualTo(status);
        assertThat(mapped.catalogCode()).isEqualTo(catalogCode);
        assertThat(mapped.errors()).singleElement().satisfies(apiError -> {
            assertThat(apiError.code()).isIn("VALIDATION_ERROR", "VALIDATION_FIELD_ERROR", "DUPLICATE_RESOURCE", "SYSTEM_RESOURCE_CONFLICT");
            assertThat(apiError.field()).isEqualTo(error.field());
        });
    }

    @Test
    void rateLimitsCarryRetryAfter() {
        assertThat(mapper.map(new AccountDomainException(AccountDomainError.UploadRateLimited.of(120)), Locale.ENGLISH).retryAfterSeconds())
                .isEqualTo(120L);
        assertThat(mapper.map(new AccountDomainException(AccountDomainError.VersionMismatch.of()), Locale.ENGLISH).retryAfterSeconds())
                .isNull();
    }

    @Test
    void reportsAllFieldsAtOnceFirstErrorPerFieldWinsInOrder() {
        messages.addMessage("first", Locale.ENGLISH, "first zip error");
        messages.addMessage("second", Locale.ENGLISH, "second zip error");
        messages.addMessage("regime", Locale.ENGLISH, "regime {0} vs {1}");

        var mapped = mapper.map(new AccountDomainException(List.of(
                AccountDomainError.InvalidField.of("address.zipCode", "first"),
                AccountDomainError.RegimeNotApplicable.of("601", "INDIVIDUAL"),
                AccountDomainError.InvalidField.of("address.zipCode", "second"))), Locale.ENGLISH);

        assertThat(mapped.errors()).extracting(ApiError::field).containsExactly("address.zipCode", "taxRegime");
        assertThat(mapped.errors().getFirst().message()).isEqualTo("first zip error");
    }
}
