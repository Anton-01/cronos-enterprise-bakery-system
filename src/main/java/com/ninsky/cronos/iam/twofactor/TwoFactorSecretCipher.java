package com.ninsky.cronos.iam.twofactor;

import com.ninsky.cronos.infrastructure.security.crypto.FieldEncryptionService;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.Optional;

/** Seals TOTP secrets with AES-GCM; the data key is unwrapped by KMS and never stored in the DB. */
@Component
public class TwoFactorSecretCipher {

    private final FieldEncryptionService encryption;

    public TwoFactorSecretCipher(FieldEncryptionService encryption) {
        this.encryption = encryption;
    }

    public byte[] seal(byte[] secret) {
        return Base64.getDecoder().decode(encryption.encrypt(Base32.encode(secret)));
    }

    /** Empty when the ciphertext cannot be opened (tampered row or lost key). */
    public Optional<byte[]> open(byte[] sealed) {
        String plain = encryption.decrypt(Base64.getEncoder().encodeToString(sealed));
        return FieldEncryptionService.DECRYPTION_FAILED_SENTINEL.equals(plain) || !Base32.isValid(plain)
                ? Optional.empty() : Optional.of(Base32.decode(plain));
    }
}
