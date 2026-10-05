package com.ninsky.cronos.iam.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamWriteFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Hash chain of the ledger (spec §7.4): {@code hash = sha256(prevHash || canonicalJson)}, where the
 * canonical JSON has sorted keys and exact decimals, so it can be recomputed from stored columns.
 */
public final class AuditHasher {

    static final ObjectMapper CANONICAL = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .nodeFactory(JsonNodeFactory.withExactBigDecimals(true))
            .build();

    private AuditHasher() {
    }

    /** Stored row fields that the hash covers. */
    public record Material(LocalDateTime createdAt, UUID actorId, String action, String category, String outcome,
                           String severity, String targetType, String targetId, String targetLabel,
                           String paramsJson, String changesJson, String reason) {
    }

    public static String hash(String prevHash, Material m) {
        ObjectNode node = CANONICAL.createObjectNode();
        node.put("createdAt", m.createdAt().toString());
        node.put("actorId", m.actorId() == null ? null : m.actorId().toString());
        node.put("action", m.action());
        node.put("category", m.category());
        node.put("outcome", m.outcome());
        node.put("severity", m.severity());
        node.put("targetType", m.targetType());
        node.put("targetId", m.targetId());
        node.put("targetLabel", m.targetLabel());
        node.set("params", parse(m.paramsJson()));
        node.set("changes", parse(m.changesJson()));
        node.put("reason", m.reason());
        return sha256((prevHash == null ? "" : prevHash) + canonical(node));
    }

    /** Sorted-key JSON of any value, used both for storage and hashing. */
    public static String toJson(Object value) {
        try {
            return value == null ? null : canonical(CANONICAL.valueToTree(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Audit payload is not serialisable", e);
        }
    }

    private static JsonNode parse(String json) {
        try {
            return json == null ? CANONICAL.nullNode() : CANONICAL.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored audit JSON is invalid", e);
        }
    }

    private static String canonical(JsonNode node) {
        try {
            return CANONICAL.writeValueAsString(CANONICAL.treeToValue(node, Object.class));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Audit JSON canonicalisation failed", e);
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
