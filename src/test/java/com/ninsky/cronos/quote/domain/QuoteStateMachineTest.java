package com.ninsky.cronos.quote.domain;

import com.ninsky.cronos.domain.entity.enums.QuoteStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Quote lifecycle (§4.1). */
class QuoteStateMachineTest {

    @ParameterizedTest
    @CsvSource({
            "DRAFT, SENT", "SENT, VIEWED", "SENT, ACCEPTED", "SENT, REJECTED", "SENT, EXPIRED", "SENT, REVOKED",
            "VIEWED, ACCEPTED", "VIEWED, REJECTED", "VIEWED, EXPIRED", "VIEWED, REVOKED", "EXPIRED, SENT"
    })
    void allowsTheContractTransitions(QuoteStatus from, QuoteStatus to) {
        assertThatCode(() -> QuoteTransitions.require(from, to)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({
            "DRAFT, ACCEPTED", "DRAFT, VIEWED", "VIEWED, SENT", "ACCEPTED, REJECTED", "REJECTED, SENT", "REVOKED, SENT",
            "EXPIRED, ACCEPTED", "ACCEPTED, EXPIRED"
    })
    void rejectsEverythingElse(QuoteStatus from, QuoteStatus to) {
        assertThatThrownBy(() -> QuoteTransitions.require(from, to))
                .isInstanceOf(QuoteTransitions.InvalidQuoteTransitionException.class);
    }

    @Test
    void finalStatusesAreTerminal() {
        assertThat(QuoteStatus.ACCEPTED.terminal()).isTrue();
        assertThat(QuoteStatus.REJECTED.terminal()).isTrue();
        assertThat(QuoteStatus.REVOKED.terminal()).isTrue();
        assertThat(QuoteStatus.EXPIRED.terminal()).isFalse();
        assertThat(QuoteStatus.openStatuses()).containsExactlyInAnyOrder(QuoteStatus.SENT, QuoteStatus.VIEWED);
    }

    @Test
    void numbersCarryTheOwnerPrefixAndTheSecond() {
        UUID owner = UUID.fromString("abcd1234-0000-0000-0000-000000000000");
        assertThat(QuoteNumbers.next(owner, LocalDateTime.of(2026, 10, 11, 9, 5, 7))).isEqualTo("CR-ABCD-20261011090507");
    }

    @Test
    void publicTokensAreLongAndUnique() {
        String first = QuoteNumbers.publicToken();
        assertThat(first).hasSize(42).matches("[0-9a-f]+");
        assertThat(QuoteNumbers.publicToken()).isNotEqualTo(first);
    }
}
