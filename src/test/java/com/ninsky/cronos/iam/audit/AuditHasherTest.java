package com.ninsky.cronos.iam.audit;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuditHasherTest {

    private static final UUID ACTOR = UUID.fromString("7b1e2c4a-0000-0000-0000-000000000001");

    @Test
    void canonicalJsonSortsKeysAndKeepsDecimalScale() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("z", new BigDecimal("16.00"));
        value.put("a", Map.of("from", "x", "to", "y"));
        assertThat(AuditHasher.toJson(value)).isEqualTo("{\"a\":{\"from\":\"x\",\"to\":\"y\"},\"z\":16.00}");
    }

    @Test
    void hashIsStableAcrossJsonFormattingAndChainsToThePrevious() {
        var compact = material("{\"rate\":16.00,\"code\":\"IVA_16\"}");
        var reformatted = material("{\"code\": \"IVA_16\", \"rate\": 16.00}");

        assertThat(AuditHasher.hash(null, compact)).isEqualTo(AuditHasher.hash(null, reformatted)).hasSize(64);
        assertThat(AuditHasher.hash("abc", compact)).isNotEqualTo(AuditHasher.hash(null, compact));
    }

    private static AuditHasher.Material material(String params) {
        return new AuditHasher.Material(LocalDateTime.of(2026, 10, 4, 10, 0, 0, 123_456_000), ACTOR, "TAX_RATE_CREATED",
                "CONFIGURATION", "SUCCESS", "NOTICE", "TAX_RATE", "1", "IVA 16 %", params, null, null);
    }
}
