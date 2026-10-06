package com.ninsky.cronos.iam.twofactor;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecoveryCodesTest {

    @Test
    void generatesTenDistinctCrockfordCodes() {
        List<String> codes = RecoveryCodes.generate();
        assertThat(codes).hasSize(10).allMatch(code -> code.matches("[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}"));
        assertThat(new HashSet<>(codes)).hasSize(10);
    }

    @Test
    void normalisesWhatPeopleType() {
        assertThat(RecoveryCodes.normalize(" 7kq4 m2xd ")).hasValue("7KQ4-M2XD");
        assertThat(RecoveryCodes.normalize("7KQ4-M2XD")).hasValue("7KQ4-M2XD");
        assertThat(RecoveryCodes.normalize("OIL0-0000")).hasValue("0110-0000");
        assertThat(RecoveryCodes.normalize("7KQ4-M2X")).isEmpty();
        assertThat(RecoveryCodes.normalize("7KQ4-M2XU")).isEmpty();
        assertThat(RecoveryCodes.normalize(null)).isEmpty();
    }

    @Test
    void sixDigitsAreATotpAnythingElseARecoveryCode() {
        assertThat(RecoveryCodes.looksLikeTotp("123456")).isTrue();
        assertThat(RecoveryCodes.looksLikeTotp(" 123456 ")).isTrue();
        assertThat(RecoveryCodes.looksLikeTotp("1234-5678")).isFalse();
        assertThat(RecoveryCodes.looksLikeTotp("12345678")).isFalse();
        assertThat(RecoveryCodes.looksLikeTotp(null)).isFalse();
    }
}
