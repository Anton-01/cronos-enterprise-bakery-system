package com.ninsky.cronos.account.shared.infrastructure.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/** {@code app.account.rate-limit.*} — per-user budgets for account-settings writes. */
@ConfigurationProperties("app.account.rate-limit")
public record AccountRateLimitProperties(
        @DefaultValue("10") int avatarUploadsPerWindow,
        @DefaultValue("30") int profileWritesPerWindow,
        @DefaultValue("30") int fiscalWritesPerWindow,
        @DefaultValue("1h") Duration window
) {
}
