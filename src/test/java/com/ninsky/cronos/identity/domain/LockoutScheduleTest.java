package com.ninsky.cronos.identity.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Progressive lockout (§2.3.1): lockoutMinutes × multiplier^(lockCount-1), capped. */
class LockoutScheduleTest {

    @ParameterizedTest
    @CsvSource({
            // minutes, multiplier, max, lockCount, expected minutes
            "15, 2, 1440, 1, 15",
            "15, 2, 1440, 2, 30",
            "15, 2, 1440, 3, 60",
            "15, 2, 1440, 7, 960",
            "15, 2, 1440, 8, 1440",
            "15, 2, 1440, 50, 1440",
            "15, 1, 1440, 9, 15",
            "60, 3, 100, 2, 100",
            "500, 2, 100, 1, 100",
            "15, 2, 1440, 0, 15"
    })
    void followsTheProgressiveSchedule(int minutes, int multiplier, int max, int lockCount, long expected) {
        assertThat(LockoutSchedule.of(minutes, multiplier, max).durationFor(lockCount)).isEqualTo(Duration.ofMinutes(expected));
    }

    @Test
    void rejectsNonPositiveParameters() {
        assertThatThrownBy(() -> LockoutSchedule.of(0, 2, 10)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void defaultsMatchTheContract() {
        assertThat(SignInPolicy.DEFAULTS.schedule().steps()).hasSize(8).containsEntry(1, Duration.ofMinutes(15));
    }
}
