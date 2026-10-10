package com.ninsky.cronos.eventing.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryScheduleTest {

    @ParameterizedTest
    @CsvSource({"1,PT1S", "2,PT5S", "3,PT30S", "4,PT2M", "5,PT10M", "6,PT1H", "9,PT1H"})
    void defaultBacksOffAndRepeatsTheLastDelay(int failures, String expected) {
        assertThat(RetrySchedule.DEFAULT.delayAfter(failures)).isEqualTo(Duration.parse(expected));
    }

    @ParameterizedTest
    @CsvSource({"1,PT1M", "2,PT5M", "3,PT30M", "4,PT2H", "5,PT6H"})
    void mailFollowsTheDocumentedSchedule(int failures, String expected) {
        assertThat(RetrySchedule.MAIL.delayAfter(failures)).isEqualTo(Duration.parse(expected));
    }

    @Test
    void exhaustionIsReachedAtMaxAttempts() {
        assertThat(RetrySchedule.DEFAULT.exhausted(9)).isFalse();
        assertThat(RetrySchedule.DEFAULT.exhausted(10)).isTrue();
        assertThat(RetrySchedule.MAIL.exhausted(5)).isFalse();
        assertThat(RetrySchedule.MAIL.exhausted(6)).isTrue();
    }

    @Test
    void rejectsNonPositiveFailureCounts() {
        assertThatThrownBy(() -> RetrySchedule.DEFAULT.delayAfter(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
