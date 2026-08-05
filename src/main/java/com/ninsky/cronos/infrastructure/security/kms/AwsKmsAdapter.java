package com.ninsky.cronos.infrastructure.security.kms;

/**
 * Stub AWS KMS adapter — interface satisfied for multi-cloud portability, not implemented.
 * Matches the Phase 1-approved precedent: interfaces + one real adapter (GCP), others stubbed.
 */
public class AwsKmsAdapter implements KmsPort {

    @Override
    public String wrapKey(byte[] plaintextKey) {
        throw new UnsupportedOperationException("AWS KMS adapter is not implemented yet");
    }

    @Override
    public byte[] unwrapKey(String wrappedKey) {
        throw new UnsupportedOperationException("AWS KMS adapter is not implemented yet");
    }
}
