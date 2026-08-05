package com.ninsky.cronos.infrastructure.security.crypto;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Locale;

/**
 * Deterministic HMAC-SHA256 blind index for exact-match lookups on encrypted columns (e.g. email).
 * The blind-index key is derived from the Data Encryption Key via HKDF-Expand (RFC 5869 §2.3,
 * single round since the 32-byte output equals the hash length) with a field-specific info string,
 * so no second key needs separate KMS wrapping/management.
 */
@Service
@RequiredArgsConstructor
public class BlindIndexService {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final FieldEncryptionService fieldEncryptionService;

    /** Deterministic index value for {@code plaintext} under the given field context (e.g. "email"). */
    public String hmac(String fieldContext, String plaintext) {
        if (plaintext == null) {
            return null;
        }
        String normalized = plaintext.trim().toLowerCase(Locale.ROOT);
        try {
            SecretKeySpec derivedKey = deriveKey(fieldContext);
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(derivedKey);
            byte[] digest = mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Blind index computation failed", e);
        }
    }

    private SecretKeySpec deriveKey(String fieldContext) throws GeneralSecurityException {
        Mac mac = Mac.getInstance(HMAC_SHA256);
        mac.init(new SecretKeySpec(fieldEncryptionService.rawKeyBytes(), HMAC_SHA256));
        byte[] info = (fieldContext + "-blind-index").getBytes(StandardCharsets.UTF_8);
        byte[] input = new byte[info.length + 1];
        System.arraycopy(info, 0, input, 0, info.length);
        input[info.length] = 0x01;
        byte[] okm = mac.doFinal(input);
        return new SecretKeySpec(okm, HMAC_SHA256);
    }
}
