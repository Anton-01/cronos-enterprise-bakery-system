package com.ninsky.cronos.iam.twofactor;

import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecondFactorTest {

    private static final UUID USER = UUID.fromString("0b9b7f8e-1c1d-4f0b-9b1e-6c2f3a4b5c6d");
    private static final byte[] SECRET = "12345678901234567890".getBytes();
    private static final byte[] SEALED = {1, 2, 3};
    private static final Instant NOW = Instant.ofEpochSecond(1_234_567_890);

    private final TwoFactorStore store = mock(TwoFactorStore.class);
    private final TwoFactorSecretCipher cipher = mock(TwoFactorSecretCipher.class);
    private final SecondFactor secondFactor = new SecondFactor(store, cipher, Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void enrolled() {
        when(store.enrolled(USER)).thenReturn(Optional.of(new TwoFactorStore.Enrolled(SEALED, null, NOW)));
        when(cipher.open(SEALED)).thenReturn(Optional.of(SECRET));
    }

    @Test
    void acceptsACurrentTotpOnceAndClaimsItsStep() {
        long step = Totp.step(NOW);
        when(store.claimStep(USER, step)).thenReturn(true, false);
        String code = Totp.code(SECRET, step);
        assertThat(secondFactor.verify(USER, code)).isEqualTo(SecondFactor.Result.TOTP);
        assertThat(secondFactor.verify(USER, code)).isEqualTo(SecondFactor.Result.INVALID_TOTP);
    }

    @Test
    void wrongTotpNeverClaimsAStep() {
        assertThat(secondFactor.verify(USER, Totp.code(SECRET, Totp.step(NOW) + 5))).isEqualTo(SecondFactor.Result.INVALID_TOTP);
        verify(store, never()).claimStep(eq(USER), anyLong());
    }

    @Test
    void recoveryCodeIsMatchedByHashAndConsumed() {
        String hash = new BCryptPasswordEncoder(4).encode("7KQ4-M2XD");
        when(store.unusedRecoveryCodes(USER)).thenReturn(List.of(new TwoFactorStore.RecoveryCode(9, hash)));
        when(store.useRecoveryCode(9, NOW)).thenReturn(true);
        assertThat(secondFactor.verify(USER, "7kq4 m2xd")).isEqualTo(SecondFactor.Result.RECOVERY_CODE);
        verify(store).useRecoveryCode(9, NOW);
    }

    @Test
    void unknownOrRacedRecoveryCodesFail() {
        String hash = new BCryptPasswordEncoder(4).encode("7KQ4-M2XD");
        when(store.unusedRecoveryCodes(USER)).thenReturn(List.of(new TwoFactorStore.RecoveryCode(9, hash)));
        assertThat(secondFactor.verify(USER, "AAAA-BBBB")).isEqualTo(SecondFactor.Result.INVALID_RECOVERY_CODE);
        when(store.useRecoveryCode(9, NOW)).thenReturn(false);
        assertThat(secondFactor.verify(USER, "7KQ4-M2XD")).isEqualTo(SecondFactor.Result.INVALID_RECOVERY_CODE);
    }

    @Test
    void requireMapsFailuresToContractErrorsOnTheGivenField() {
        String wrongTotp = Totp.code(SECRET, Totp.step(NOW) + 5);
        assertThatThrownBy(() -> secondFactor.require(USER, wrongTotp, "code")).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.primaryCode()).isEqualTo(ApiErrorCode.INVALID_TOTP_CODE);
            assertThat(e.violations().getFirst().field()).isEqualTo("code");
        });
        assertThatThrownBy(() -> secondFactor.require(USER, "XXXX-YYYY", "code")).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.primaryCode()).isEqualTo(ApiErrorCode.INVALID_RECOVERY_CODE));
    }

    @Test
    void issuedRecoveryCodesAreStoredAsHashesOnly() {
        List<String> codes = secondFactor.issueRecoveryCodes(USER);
        assertThat(codes).hasSize(10);
        ArgumentCaptor<Collection<String>> hashes = ArgumentCaptor.captor();
        verify(store).replaceRecoveryCodes(eq(USER), hashes.capture());
        assertThat(hashes.getValue()).hasSize(10).allMatch(hash -> hash.startsWith("$2")).doesNotContainAnyElementsOf(codes);
    }
}
