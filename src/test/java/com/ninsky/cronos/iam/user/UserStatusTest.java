package com.ninsky.cronos.iam.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class UserStatusTest {

    @ParameterizedTest
    @CsvSource({
            "PENDING_ACTIVATION, ACTIVE, true",
            "PENDING_ACTIVATION, DEACTIVATED, true",
            "PENDING_ACTIVATION, SUSPENDED, false",
            "ACTIVE, SUSPENDED, true",
            "ACTIVE, LOCKED, true",
            "ACTIVE, DEACTIVATED, true",
            "ACTIVE, PENDING_ACTIVATION, false",
            "SUSPENDED, ACTIVE, true",
            "SUSPENDED, LOCKED, false",
            "LOCKED, ACTIVE, true",
            "LOCKED, DEACTIVATED, true",
            "DEACTIVATED, ACTIVE, true",
            "DEACTIVATED, SUSPENDED, false"
    })
    void followsTheLifecycle(UserStatus from, UserStatus to, boolean allowed) {
        assertThat(from.canMoveTo(to)).isEqualTo(allowed);
    }

    @Test
    void onlyTemporaryStatesAcceptAnEndDate() {
        assertThat(Arrays.stream(UserStatus.values()).filter(UserStatus::acceptsUntil))
                .containsExactlyInAnyOrder(UserStatus.SUSPENDED, UserStatus.LOCKED);
    }
}
