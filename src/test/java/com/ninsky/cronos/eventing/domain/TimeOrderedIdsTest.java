package com.ninsky.cronos.eventing.domain;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TimeOrderedIdsTest {

    @Test
    void isVersion7WithTheRfcVariant() {
        UUID id = TimeOrderedIds.next(Clock.systemUTC());
        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void sortsByMillisecond() {
        UUID earlier = TimeOrderedIds.next(Clock.fixed(Instant.parse("2026-10-10T10:00:00.001Z"), ZoneOffset.UTC));
        UUID later = TimeOrderedIds.next(Clock.fixed(Instant.parse("2026-10-10T10:00:00.002Z"), ZoneOffset.UTC));
        assertThat(earlier.toString()).isLessThan(later.toString());
        assertThat(TimeOrderedIds.lowerBound(Instant.parse("2026-10-10T10:00:00.002Z").toEpochMilli()).toString())
                .isLessThanOrEqualTo(later.toString())
                .isGreaterThan(earlier.toString());
    }
}
