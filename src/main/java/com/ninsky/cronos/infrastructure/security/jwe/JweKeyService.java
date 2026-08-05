package com.ninsky.cronos.infrastructure.security.jwe;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.ninsky.cronos.infrastructure.config.security.JweConfig;
import com.ninsky.cronos.infrastructure.security.kms.KmsPort;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.UUID;

/**
 * Holds the server's EC (P-256) key pair used for JWE: clients encrypt requests against the public
 * half (via {@code GET /security/jwe-public-key}); the server decrypts with the private half. The
 * private key is envelope-wrapped via the same {@link KmsPort} Phase 4a introduced for the field-
 * encryption DEK — one KMS abstraction, reused, not a second key-management story.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JweKeyService {

    private final JweConfig jweConfig;
    private final KmsPort kmsPort;

    private ECKey keyPair;

    @PostConstruct
    void init() {
        String wrapped = jweConfig.getWrappedPrivateKey();
        this.keyPair = (wrapped == null || wrapped.isBlank()) ? generateAndWrap() : unwrap(wrapped);
    }

    private ECKey generateAndWrap() {
        try {
            ECKey generated = new ECKeyGenerator(Curve.P_256).keyID(UUID.randomUUID().toString()).generate();
            String wrapped = kmsPort.wrapKey(generated.toJSONString().getBytes(StandardCharsets.UTF_8));
            log.warn("jwe.wrapped-private-key not configured — generated a new EC key pair for this " +
                    "process only. It will NOT survive a restart. Set jwe.wrapped-private-key={} to " +
                    "persist it (dev/sandbox use only, never for a real deployment).", wrapped);
            return generated;
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to generate JWE key pair", e);
        }
    }

    private ECKey unwrap(String wrapped) {
        byte[] plaintext = kmsPort.unwrapKey(wrapped);
        try {
            return ECKey.parse(new String(plaintext, StandardCharsets.UTF_8));
        } catch (ParseException e) {
            throw new IllegalStateException("Failed to parse configured JWE key pair", e);
        }
    }

    /** Full key pair (with private material) — for decrypting requests. */
    public ECKey getKeyPair() {
        return keyPair;
    }

    /** Public half only — safe to expose via the discovery endpoint. */
    public ECKey getPublicJwk() {
        return keyPair.toPublicJWK();
    }
}
