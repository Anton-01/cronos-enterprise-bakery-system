package com.ninsky.cronos.infrastructure.security.kms;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Dev/sandbox-only KMS stand-in: wraps/unwraps the Data Encryption Key with a local AES-256-GCM
 * master key instead of calling a real cloud KMS API. Active only when {@code kms.provider=local}.
 * This is what makes the envelope-encryption code path actually exercisable without real cloud
 * credentials — same honesty pattern as {@code ddl-auto=update} standing in for {@code validate}.
 * A real deployment must use {@code gcp}/{@code aws}/{@code azure} instead.
 */
@Slf4j
public class LocalDevKmsAdapter implements KmsPort {

    private static final String AES = "AES";
    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final SecretKeySpec masterKey;

    public LocalDevKmsAdapter(String configuredMasterKeyBase64) {
        byte[] keyBytes;
        if (configuredMasterKeyBase64 == null || configuredMasterKeyBase64.isBlank()) {
            keyBytes = new byte[32];
            new SecureRandom().nextBytes(keyBytes);
            log.warn("kms.local.master-key not configured — generated a random master key for this " +
                    "process only. It will NOT survive a restart. Set kms.local.master-key={} to persist it " +
                    "(dev/sandbox use only, never for a real deployment).", Base64.getEncoder().encodeToString(keyBytes));
        } else {
            keyBytes = Base64.getDecoder().decode(configuredMasterKeyBase64);
        }
        this.masterKey = new SecretKeySpec(keyBytes, AES);
    }

    @Override
    public String wrapKey(byte[] plaintextKey) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintextKey);

            byte[] packed = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, packed, 0, iv.length);
            System.arraycopy(ciphertext, 0, packed, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(packed);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to wrap key with local dev KMS", e);
        }
    }

    @Override
    public byte[] unwrapKey(String wrappedKey) {
        try {
            byte[] packed = Base64.getDecoder().decode(wrappedKey);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] ciphertext = new byte[packed.length - GCM_IV_LENGTH];
            System.arraycopy(packed, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(packed, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to unwrap key with local dev KMS", e);
        }
    }
}
