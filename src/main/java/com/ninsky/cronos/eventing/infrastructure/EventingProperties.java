package com.ninsky.cronos.eventing.infrastructure;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * {@code app.eventing.*}: outbox relay tuning and retention.
 *
 * @param relayEnabled      false to stop dispatching on this instance (publishing keeps working)
 * @param pollInterval      fallback poll period; commits also wake the relay immediately
 * @param batchSize         messages claimed per round
 * @param concurrency       messages of one batch dispatched in parallel (different aggregates only)
 * @param lease             how long a claimed message is hidden from other relays
 * @param dispatchedRetention how long DISPATCHED messages are kept for the event trail
 */
@Validated
@ConfigurationProperties("app.eventing")
public record EventingProperties(
        @DefaultValue("true") boolean relayEnabled,
        @DefaultValue("500ms") @NotNull Duration pollInterval,
        @DefaultValue("100") @Min(1) @Max(1000) int batchSize,
        @DefaultValue("8") @Min(1) @Max(64) int concurrency,
        @DefaultValue("5m") @NotNull Duration lease,
        @DefaultValue("30d") @NotNull Duration dispatchedRetention
) {
}
