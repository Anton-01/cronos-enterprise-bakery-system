package com.ninsky.cronos.infrastructure.security.crypto;

import com.ninsky.cronos.infrastructure.config.kms.KmsConfig;
import com.ninsky.cronos.infrastructure.security.kms.KmsPort;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.AEADBadTagException;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
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

    /**
     * Returned by {@link #decrypt} in place of throwing when ciphertext fails AES-GCM
     * verification, so a corrupted/un-decryptable row degrades gracefully instead of blowing up
     * Hibernate's attribute-conversion lifecycle. Callers on the read path that can't tolerate a
     * corrupted value (e.g. {@code AuthenticationService.login}) must check for this sentinel
     * explicitly and reject the request (401), rather than let it silently pass through as if it
     * were the real plaintext.
     */
    public static final String DECRYPTION_FAILED_SENTINEL = "[DECRYPTION_FAILED]";

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
     * illegal in Base64) is returned as-is rather than crashing. This heuristic only reliably
     * covers fields whose plaintext form is guaranteed to contain non-Base64 characters (e.g.
     * email); it will not catch legacy plaintext in fields like a Base32 TOTP secret, which
     * happens to be valid Base64 and so falls through to the AES-GCM attempt below.
     * <p>
     * A value that IS valid Base64 but fails AES-GCM tag verification — a rotated/lost
     * data-encryption-key, or genuinely tampered/corrupted ciphertext — returns
     * {@link #DECRYPTION_FAILED_SENTINEL} instead of throwing. This method is called from
     * {@code EncryptedStringConverter.convertToEntityAttribute}, i.e. mid-Hibernate-hydration on
     * every JPA load of an entity with an encrypted column: throwing here surfaces as an opaque
     * {@code JpaSystemException} (500) on any code path that merely loads the entity, including
     * pre-authentication ones like {@code AuthenticationService.login}. Degrading gracefully lets
     * the caller decide — {@code login} rejects a corrupted row as a 401 like any other invalid
     * credential, instead of every login attempt against that row taking down the request with a
     * 500. Other {@link GeneralSecurityException}s (bad key, bad algorithm params — a cipher
     * *configuration* problem affecting every row, not one corrupted value) still throw: masking
     * that as a wave of per-row "corrupted data" would hide a systemic outage instead of paging it.
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
        } catch (AEADBadTagException e) {
            // Deliberately no `e`/stack trace and no ciphertext in the log line — the trace is
            // just noise for an expected-shape failure, and the payload is sensitive either way.
            log.warn("Failed to decrypt field due to GCM tag mismatch. The encryption key may have " +
                    "changed, or the stored value was tampered with. Treating as corrupted data.");
            return DECRYPTION_FAILED_SENTINEL;
        } catch (BadPaddingException | IllegalBlockSizeException e) {
            log.warn("Failed to decrypt field: ciphertext is malformed or was encrypted under a " +
                    "different key. Treating as corrupted data.");
            return DECRYPTION_FAILED_SENTINEL;
        } catch (GeneralSecurityException e) {
            // Anything else (bad key, bad algorithm params, ...) is a cipher/config problem that
            // would affect every row, not this one — fail loudly instead of masking a systemic
            // outage as a wave of per-row "corrupted data" 401s.
            throw new IllegalStateException("Field decryption failed due to a cipher configuration error", e);
        }
    }

    /** Exposes the raw DEK bytes for {@link BlindIndexService} to derive its HMAC key from via HKDF. */
    byte[] rawKeyBytes() {
        return dataEncryptionKey.getEncoded();
    }
}
