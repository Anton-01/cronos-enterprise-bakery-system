package com.ninsky.cronos.identity.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class LoginIdentifierTest {

    @ParameterizedTest
    @CsvSource({
            "'  Ana.Lopez  ', ana.lopez, false",
            "ANA@Cronos.MX, ana@cronos.mx, true",
            "'ｆｕｌｌｗｉｄｔｈ', fullwidth, false"
    })
    void normalisesAndDetectsEmails(String raw, String expected, boolean email) {
        LoginIdentifier identifier = LoginIdentifier.resolve(raw, null).orElseThrow();
        assertThat(identifier.value()).isEqualTo(expected);
        assertThat(identifier.email()).isEqualTo(email);
    }

    @Test
    void identifierWinsOverTheLegacyUsername() {
        assertThat(LoginIdentifier.resolve("new", "old")).map(LoginIdentifier::value).contains("new");
        assertThat(LoginIdentifier.resolve(null, "old")).map(LoginIdentifier::value).contains("old");
        assertThat(LoginIdentifier.resolve("  ", null)).isEmpty();
    }
}
