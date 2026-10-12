package com.ninsky.cronos.eventing.domain;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

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
        UUID earlier = TimeOrderedIds.next(Clock.fixed(Instant.parse("2099-10-10T10:00:00.001Z"), ZoneOffset.UTC));
        UUID later = TimeOrderedIds.next(Clock.fixed(Instant.parse("2099-10-10T10:00:00.002Z"), ZoneOffset.UTC));
        assertThat(earlier.toString()).isLessThan(later.toString());
        assertThat(TimeOrderedIds.lowerBound(Instant.parse("2099-10-10T10:00:00.002Z").toEpochMilli()).toString())
                .isLessThanOrEqualTo(later.toString())
                .isGreaterThan(earlier.toString());
    }

    @Test
    void idsOfOneMillisecondAreStrictlyIncreasing() {
        Clock frozen = Clock.fixed(Instant.parse("2026-10-10T10:00:00.003Z"), ZoneOffset.UTC);
        List<String> ids = IntStream.range(0, 5000).mapToObj(i -> TimeOrderedIds.next(frozen).toString()).toList();
        assertThat(ids).isSorted().doesNotHaveDuplicates();
        assertThat(TimeOrderedIds.next(frozen).version()).isEqualTo(7);
    }

    @Test
    void aClockSteppingBackDoesNotBreakTheOrder() {
        UUID later = TimeOrderedIds.next(Clock.fixed(Instant.parse("2026-10-10T10:00:01Z"), ZoneOffset.UTC));
        UUID afterStepBack = TimeOrderedIds.next(Clock.fixed(Instant.parse("2026-10-10T10:00:00Z"), ZoneOffset.UTC));
        assertThat(afterStepBack.toString()).isGreaterThan(later.toString());
    }
}
