package com.ninsky.cronos.iam.audit;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuditChainVerifierTest {

    private static AuditHasher.Material material(String action, String params) {
        return new AuditHasher.Material(LocalDateTime.of(2026, 10, 5, 3, 30, 0, 123_000), UUID.randomUUID(), action,
                "SECURITY", "SUCCESS", "INFO", "USER", "42", "jperez", params, null, null);
    }

    @Test
    void intactChainPasses() {
        AuditChainVerifier.Chain chain = new AuditChainVerifier.Chain();
        AuditHasher.Material first = material("LOGIN_SUCCEEDED", "{\"detail\":1}");
        AuditHasher.Material second = material("LOGOUT", null);
        String h1 = AuditHasher.hash("legacy-tail", first);
        chain.accept(10, "legacy-tail", h1, first);
        chain.accept(11, h1, AuditHasher.hash(h1, second), second);
        assertThat(chain.rows).isEqualTo(2);
        assertThat(chain.brokenAtId).isNull();
    }

    @Test
    void jsonKeyOrderAndSpacingDoNotMatter() {
        AuditHasher.Material stored = material("AUDIT_EXPORT", "{\"b\": 2, \"a\": 1}");
        AuditHasher.Material written = material("AUDIT_EXPORT", "{\"a\":1,\"b\":2}");
        AuditChainVerifier.Chain chain = new AuditChainVerifier.Chain();
        chain.accept(1, null, AuditHasher.hash(null, written), new AuditHasher.Material(written.createdAt(), written.actorId(),
                stored.action(), stored.category(), stored.outcome(), stored.severity(), stored.targetType(), stored.targetId(),
                stored.targetLabel(), stored.paramsJson(), null, null));
        assertThat(chain.brokenAtId).isNull();
    }

    @Test
    void tamperedRowIsReported() {
        AuditChainVerifier.Chain chain = new AuditChainVerifier.Chain();
        AuditHasher.Material original = material("ROLE_UPDATED", "{\"detail\":\"x\"}");
        String h1 = AuditHasher.hash(null, original);
        AuditHasher.Material tampered = new AuditHasher.Material(original.createdAt(), original.actorId(), original.action(),
                original.category(), original.outcome(), original.severity(), original.targetType(), original.targetId(),
                "someone else", original.paramsJson(), null, null);
        chain.accept(5, null, h1, tampered);
        assertThat(chain.brokenAtId).isEqualTo(5L);
    }

    @Test
    void brokenLinkIsReported() {
        AuditChainVerifier.Chain chain = new AuditChainVerifier.Chain();
        AuditHasher.Material a = material("LOGIN_SUCCEEDED", null);
        AuditHasher.Material b = material("LOGOUT", null);
        String h1 = AuditHasher.hash(null, a);
        chain.accept(1, null, h1, a);
        chain.accept(3, "deleted-row-hash", AuditHasher.hash("deleted-row-hash", b), b);
        assertThat(chain.brokenAtId).isEqualTo(3L);
    }
}
