package com.ninsky.cronos.identity.infrastructure;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * {@code app.auth.*}: identity settings that are deployment concerns rather than tenant policy.
 *
 * @param termsVersion        the terms a self-registration must accept ({@code acceptedTermsVersion})
 * @param rateLimitsEnabled   false only in tests that replay many sign-ins
 * @param anonymousMinLatency minimum duration of anonymous answers that must not reveal whether an account exists
 * @param twoFactorChallengeTtl lifetime of the second sign-in step
 */
@Validated
@ConfigurationProperties("app.auth")
public record AuthProperties(
        @DefaultValue("2026-10") @NotBlank String termsVersion,
        @DefaultValue("true") boolean rateLimitsEnabled,
        @DefaultValue("300ms") @NotNull Duration anonymousMinLatency,
        @DefaultValue("5m") @NotNull Duration twoFactorChallengeTtl
) {
}
