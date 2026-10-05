package com.ninsky.cronos.iam.twofactor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Base32Test {

    @ParameterizedTest
    @CsvSource({"f, MY", "fo, MZXQ", "foo, MZXW6", "foob, MZXW6YQ", "fooba, MZXW6YTB", "foobar, MZXW6YTBOI"})
    void encodesRfc4648VectorsWithoutPadding(String plain, String encoded) {
        assertThat(Base32.encode(plain.getBytes(StandardCharsets.US_ASCII))).isEqualTo(encoded);
        assertThat(new String(Base32.decode(encoded + "===="), StandardCharsets.US_ASCII)).isEqualTo(plain);
    }

    @Test
    void twentyByteSecretsRoundTripAs32Characters() {
        byte[] secret = new byte[20];
        new SecureRandom().nextBytes(secret);
        String text = Base32.encode(secret);
        assertThat(text).hasSize(32).matches("[A-Z2-7]+");
        assertThat(Base32.decode(text.toLowerCase())).isEqualTo(secret);
        assertThat(Base32.isValid(text)).isTrue();
    }

    @Test
    void rejectsForeignCharacters() {
        assertThatThrownBy(() -> Base32.decode("MZXW6!")).isInstanceOf(IllegalArgumentException.class);
        assertThat(Base32.isValid("MZXW6")).isFalse();
        assertThat(Base32.isValid(null)).isFalse();
    }
}
