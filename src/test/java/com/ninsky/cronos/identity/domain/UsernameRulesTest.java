package com.ninsky.cronos.identity.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class UsernameRulesTest {

    @ParameterizedTest
    @CsvSource({"ana, true", "ana.lopez_9, true", "Ana, true", "ab, false", "9ana, false", "ana lopez, false",
            "abcdefghijklmnopqrstuvwxyzabcdefg, false"})
    void shape(String raw, boolean valid) {
        assertThat(UsernameRules.wellFormed(UsernameRules.normalize(raw))).isEqualTo(valid);
    }

    @ParameterizedTest
    @CsvSource({"admin, true", "Root, true", "cronos, true", "ana, false"})
    void reservedNames(String raw, boolean reserved) {
        assertThat(UsernameRules.reserved(UsernameRules.normalize(raw))).isEqualTo(reserved);
    }
}
