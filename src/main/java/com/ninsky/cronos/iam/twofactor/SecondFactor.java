package com.ninsky.cronos.iam.twofactor;

import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Checks a second factor: a 6-digit TOTP (±1 step, each step usable once) or an unused recovery
 * code (consumed on success). Shared by sign-in, disable and recovery-code regeneration.
 */
@Component
public class SecondFactor {

    public enum Result {
        TOTP, RECOVERY_CODE, INVALID_TOTP, INVALID_RECOVERY_CODE;

        public boolean accepted() {
            return this == TOTP || this == RECOVERY_CODE;
        }
    }

    /** Recovery codes carry 40 random bits; cost 10 keeps a 10-code scan fast. */
    private static final PasswordEncoder RECOVERY_HASHER = new BCryptPasswordEncoder(10);

    private final TwoFactorStore store;
    private final TwoFactorSecretCipher cipher;
    private final Clock clock;

    public SecondFactor(TwoFactorStore store, TwoFactorSecretCipher cipher, Clock clock) {
        this.store = store;
        this.cipher = cipher;
        this.clock = clock;
    }

    public Result verify(UUID userId, String code) {
        if (RecoveryCodes.looksLikeTotp(code)) {
            return verifyTotp(userId, code.trim()) ? Result.TOTP : Result.INVALID_TOTP;
        }
        return useRecoveryCode(userId, code) ? Result.RECOVERY_CODE : Result.INVALID_RECOVERY_CODE;
    }

    /** Throws the contract's 400 on {@code field} unless the code is accepted. */
    public Result require(UUID userId, String code, String field) {
        Result result = verify(userId, code);
        if (!result.accepted()) {
            throw result == Result.INVALID_TOTP
                    ? ApiException.of(ApiErrorCode.INVALID_TOTP_CODE, field, "security.twoFactor.invalidTotp")
                    : ApiException.of(ApiErrorCode.INVALID_RECOVERY_CODE, field, "security.twoFactor.invalidRecoveryCode");
        }
        return result;
    }

    /** Fresh codes replace every previous one; returns the plain codes to show once. */
    public List<String> issueRecoveryCodes(UUID userId) {
        List<String> codes = RecoveryCodes.generate();
        store.replaceRecoveryCodes(userId, codes.stream().map(RECOVERY_HASHER::encode).toList());
        return codes;
    }

    private boolean verifyTotp(UUID userId, String code) {
        Optional<byte[]> secret = store.enrolled(userId).flatMap(enrolled -> cipher.open(enrolled.secretEnc()));
        if (secret.isEmpty()) {
            return false;
        }
        OptionalLong step = Totp.matchingStep(secret.get(), code, clock.instant());
        return step.isPresent() && store.claimStep(userId, step.getAsLong());
    }

    private boolean useRecoveryCode(UUID userId, String input) {
        return RecoveryCodes.normalize(input)
                .flatMap(code -> store.unusedRecoveryCodes(userId).stream()
                        .filter(stored -> RECOVERY_HASHER.matches(code, stored.hash())).findFirst())
                .map(stored -> store.useRecoveryCode(stored.id(), clock.instant()))
                .orElse(false);
    }
}
