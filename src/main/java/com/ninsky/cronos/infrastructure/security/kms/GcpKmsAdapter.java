package com.ninsky.cronos.infrastructure.security.kms;

import com.google.cloud.kms.v1.CryptoKeyName;
import com.google.cloud.kms.v1.DecryptResponse;
import com.google.cloud.kms.v1.EncryptResponse;
import com.google.cloud.kms.v1.KeyManagementServiceClient;
import com.google.protobuf.ByteString;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;

/**
 * Real GCP Cloud KMS adapter: wraps/unwraps the local Data Encryption Key via a configured
 * key ring/key. Not exercised against live credentials in the sandbox verification for this
 * phase (none available here) — {@code LocalDevKmsAdapter} stands in for that; this class
 * follows the real Cloud KMS API shape so it's ready for an actual GCP project.
 */
@Slf4j
public class GcpKmsAdapter implements KmsPort {

    private final KeyManagementServiceClient client;
    private final CryptoKeyName keyName;

    public GcpKmsAdapter(String projectId, String location, String keyRing, String keyId) {
        try {
            this.client = KeyManagementServiceClient.create();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create GCP KeyManagementServiceClient", e);
        }
        this.keyName = CryptoKeyName.of(projectId, location, keyRing, keyId);
    }

    @Override
    public String wrapKey(byte[] plaintextKey) {
        EncryptResponse response = client.encrypt(keyName, ByteString.copyFrom(plaintextKey));
        return Base64.getEncoder().encodeToString(response.getCiphertext().toByteArray());
    }

    @Override
    public byte[] unwrapKey(String wrappedKey) {
        ByteString ciphertext = ByteString.copyFrom(Base64.getDecoder().decode(wrappedKey));
        DecryptResponse response = client.decrypt(keyName, ciphertext);
        return response.getPlaintext().toByteArray();
    }

    @PreDestroy
    public void close() {
        client.close();
    }
}
