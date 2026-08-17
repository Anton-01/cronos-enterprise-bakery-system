package com.ninsky.cronos.infrastructure.security.crypto;

import com.ninsky.cronos.infrastructure.config.kms.KmsConfig;
import com.ninsky.cronos.infrastructure.security.kms.KmsPort;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Field-level encryption via envelope encryption: the Data Encryption Key (DEK) is unwrapped
 * once at startup through {@link KmsPort} and held in memory only (never persisted in plaintext).
 * Every {@link #encrypt}/{@link #decrypt} call does local AES-256-GCM with the DEK — no per-field
 * KMS round-trip.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FieldEncryptionService {

    private static final String AES = "AES";
    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int DEK_LENGTH_BYTES = 32;

    private final KmsPort kmsPort;
    private final KmsConfig kmsConfig;

    private SecretKeySpec dataEncryptionKey;

    @PostConstruct
    void init() {
        String wrappedDataKey = kmsConfig.getWrappedDataKey();
        byte[] plaintextDek;
        if (wrappedDataKey == null || wrappedDataKey.isBlank()) {
            plaintextDek = new byte[DEK_LENGTH_BYTES];
            new SecureRandom().nextBytes(plaintextDek);
            String wrapped = kmsPort.wrapKey(plaintextDek);
            log.warn("kms.wrapped-data-key not configured — generated and wrapped a new Data " +
                    "Encryption Key for this process only. Data encrypted now will NOT be readable " +
                    "after a restart unless you persist kms.wrapped-data-key={}", wrapped);
        } else {
            plaintextDek = kmsPort.unwrapKey(wrappedDataKey);
        }
        this.dataEncryptionKey = new SecretKeySpec(plaintextDek, AES);
    }

    /** Returns the plaintext value unchanged if null; otherwise base64(IV || ciphertext || authTag). */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, dataEncryptionKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            byte[] packed = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, packed, 0, iv.length);
            System.arraycopy(ciphertext, 0, packed, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(packed);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Field encryption failed", e);
        }
    }

    /**
     * Tolerates legacy plain-text values written before {@code EncryptedStringConverter} existed:
     * anything that isn't valid Base64 (real emails always contain {@code @}/{@code .}, both
     * illegal in Base64) is returned as-is rather than crashing. A value that IS valid Base64 but
     * fails GCM tag verification is NOT treated as plaintext — that's ambiguous with genuine
     * corruption or a rotated/lost data-encryption-key, so it still throws loudly. This heuristic
     * only reliably covers fields whose plaintext form is guaranteed to contain non-Base64
     * characters (e.g. email); it will not catch legacy plaintext in fields like a Base32 TOTP
     * secret, which happens to be valid Base64 and so still hits the throwing branch below.
     */
    public String decrypt(String ciphertextBase64) {
        if (ciphertextBase64 == null) {
            return null;
        }
        byte[] packed;
        try {
            packed = Base64.getDecoder().decode(ciphertextBase64);
        } catch (IllegalArgumentException e) {
            log.warn("Stored value is not valid Base64 ciphertext — treating as legacy plain-text " +
                    "data written before field encryption was introduced. Re-encrypt this row to migrate it.");
            return ciphertextBase64;
        }
        if (packed.length <= GCM_IV_LENGTH) {
            log.warn("Stored value is too short to contain an IV + ciphertext — treating as legacy " +
                    "plain-text data written before field encryption was introduced.");
            return ciphertextBase64;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] ciphertext = new byte[packed.length - GCM_IV_LENGTH];
            System.arraycopy(packed, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(packed, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.DECRYPT_MODE, dataEncryptionKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, java.nio.charset.StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Field decryption failed: the stored value is valid Base64 " +
                    "but fails AES-GCM tag verification. This usually means the data-encryption-key " +
                    "changed since the value was encrypted (e.g. kms.local.master-key/kms.wrapped-data-key " +
                    "not persisted across a restart) rather than legacy plain-text data.", e);
        }
    }

    /** Exposes the raw DEK bytes for {@link BlindIndexService} to derive its HMAC key from via HKDF. */
    byte[] rawKeyBytes() {
        return dataEncryptionKey.getEncoded();
    }
}
