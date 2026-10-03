package com.ninsky.cronos.account.profile.domain;

import com.ninsky.cronos.account.shared.domain.DomainValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class E164PhoneTest {

    @ParameterizedTest
    @ValueSource(strings = {"+525512345678", "+14155552671", "+442071838750", " +525512345678 "})
    void acceptsRealInternationalNumbers(String candidate) {
        assertThat(E164Phone.isValid(candidate)).isTrue();
        assertThat(new E164Phone(candidate).value()).isEqualTo(candidate.strip());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "5512345678",        // no country code
            "+05512345678",      // leading zero after +
            "+52 55 1234 5678",  // spaces: not E.164
            "+1234",             // too short for the regex
            "+1234567890123456", // 16 digits
            "+10000000000",      // right shape, not a real number (libphonenumber)
            "+52abc"})
    void rejectsAnythingThatIsNotAValidE164Number(String candidate) {
        assertThat(E164Phone.isValid(candidate)).isFalse();
        assertThatThrownBy(() -> new E164Phone(candidate))
                .isInstanceOf(DomainValidationException.class)
                .hasMessage("account.profile.phoneNumber.invalid");
    }

    @Test
    void blankMeansNoPhone() {
        assertThat(E164Phone.ofNullable(null)).isNull();
        assertThat(E164Phone.ofNullable("  ")).isNull();
    }

    @ParameterizedTest(name = "legacy {0} -> {1}")
    @CsvSource({
            "5512345678, +525512345678",
            "55 1234 5678, +525512345678",
            "+14155552671, +14155552671"})
    void legacyNationalDigitsAreInterpretedAsMexican(String legacy, String expected) {
        assertThat(E164Phone.fromLegacy(legacy, "MX")).map(E164Phone::value).contains(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "abc", ""})
    void unparsableLegacyValuesAreEmpty(String legacy) {
        assertThat(E164Phone.fromLegacy(legacy, "MX")).isEmpty();
    }
}
