package com.ninsky.cronos.eventing.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ninsky.cronos.infrastructure.security.crypto.FieldEncryptionService;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Encrypts the {@code @Sensitive} components of an outbox payload at rest (AES-256-GCM, the column
 * encryption key of {@link FieldEncryptionService}), restores them for handlers and strips them for the trail.
 */
@Component
public class SensitivePayloadCodec {

    static final String PREFIX = "enc:";

    private final FieldEncryptionService encryption;
    private final ObjectMapper json;

    public SensitivePayloadCodec(FieldEncryptionService encryption, ObjectMapper json) {
        this.encryption = encryption;
        this.json = json;
    }

    /** Replaces each sensitive component by {@code enc:<ciphertext>} of its JSON; returns {@code payload}. */
    public ObjectNode seal(ObjectNode payload, Set<String> sensitive) {
        for (String name : sensitive) {
            JsonNode value = payload.get(name);
            if (value != null && !value.isNull()) {
                payload.put(name, PREFIX + encryption.encrypt(write(value)));
            }
        }
        return payload;
    }

    /**
     * Inverse of {@link #seal}; returns {@code payload}.
     *
     * @throws IllegalStateException when a sealed value cannot be decrypted (key rotated or data corrupted)
     */
    public ObjectNode open(ObjectNode payload, Set<String> sensitive) {
        for (String name : sensitive) {
            JsonNode value = payload.get(name);
            if (value != null && value.isTextual() && value.asText().startsWith(PREFIX)) {
                String plain = encryption.decrypt(value.asText().substring(PREFIX.length()));
                if (plain == null || FieldEncryptionService.DECRYPTION_FAILED_SENTINEL.equals(plain)) {
                    throw new IllegalStateException("Sensitive payload component '" + name + "' cannot be decrypted");
                }
                payload.set(name, read(plain));
            }
        }
        return payload;
    }

    /** A copy of {@code payload} without the sensitive components (event trail). */
    public ObjectNode redact(ObjectNode payload, Set<String> sensitive) {
        ObjectNode copy = payload.deepCopy();
        copy.remove(sensitive);
        return copy;
    }

    private String write(JsonNode node) {
        try {
            return json.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise a sensitive payload component", e);
        }
    }

    private JsonNode read(String text) {
        try {
            return json.readTree(text);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot parse a sensitive payload component", e);
        }
    }
}
