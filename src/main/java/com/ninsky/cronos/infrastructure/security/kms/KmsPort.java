package com.ninsky.cronos.infrastructure.security.kms;

/**
 * Envelope-encryption key management: a KMS only ever protects a small Data Encryption Key (DEK).
 * The DEK itself does the actual per-field AES work locally ({@code FieldEncryptionService}) —
 * calling out to a cloud KMS API for every field on every row would be slow and rate-limited.
 */
public interface KmsPort {

    /** Wraps (encrypts) a plaintext key with the KMS-managed key-encryption key. */
    String wrapKey(byte[] plaintextKey);

    /** Unwraps (decrypts) a previously-wrapped key back to its plaintext bytes. */
    byte[] unwrapKey(String wrappedKey);
}
