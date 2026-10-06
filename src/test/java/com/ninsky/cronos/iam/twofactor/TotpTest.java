package com.ninsky.cronos.iam.twofactor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TotpTest {

    /** RFC 6238 appendix B SHA1 seed. */
    private static final byte[] SEED = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @ParameterizedTest
    @CsvSource({"59, 287082", "1111111109, 081804", "1111111111, 050471", "1234567890, 005924", "2000000000, 279037"})
    void matchesRfc6238Vectors(long epochSecond, String expected) {
        assertThat(Totp.code(SEED, Totp.step(Instant.ofEpochSecond(epochSecond)))).isEqualTo(expected);
    }

    @Test
    void acceptsOneStepOfDriftEitherWay() {
        Instant now = Instant.ofEpochSecond(1_234_567_890);
        long step = Totp.step(now);
        assertThat(Totp.matchingStep(SEED, Totp.code(SEED, step - 1), now)).hasValue(step - 1);
        assertThat(Totp.matchingStep(SEED, Totp.code(SEED, step + 1), now)).hasValue(step + 1);
        assertThat(Totp.matchingStep(SEED, Totp.code(SEED, step - 2), now)).isEmpty();
        assertThat(Totp.matchingStep(SEED, Totp.code(SEED, step + 2), now)).isEmpty();
    }

    @Test
    void rejectsMalformedCodes() {
        Instant now = Instant.ofEpochSecond(59);
        assertThat(Totp.matchingStep(SEED, null, now)).isEmpty();
        assertThat(Totp.matchingStep(SEED, "28708", now)).isEmpty();
        assertThat(Totp.matchingStep(SEED, "2870822", now)).isEmpty();
        assertThat(Totp.matchingStep(SEED, "abcdef", now)).isEmpty();
    }
}
