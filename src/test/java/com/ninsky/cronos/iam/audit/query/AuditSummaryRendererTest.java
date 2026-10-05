package com.ninsky.cronos.iam.audit.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditSummaryRendererTest {

    private static final Locale ES = Locale.forLanguageTag("es-MX");
    private static final UUID ACTOR = UUID.fromString("6d1f6f0e-2d55-4c1a-9a0c-3b8f4f6f2a11");

    private final AuditSummaryRenderer renderer = new AuditSummaryRenderer(messages(), new ObjectMapper());

    private static ResourceBundleMessageSource messages() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasenames("i18n/iam", "i18n/security");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        return source;
    }

    private static AuditRow row(String action, UUID actorId, String actorUsername, String actorLabel, String targetId,
                                String targetLabel, String params) {
        return new AuditRow(1, LocalDateTime.of(2026, 10, 5, 9, 0), "SECURITY", action, "SUCCESS", "INFO", actorId,
                actorUsername, actorLabel, null, "USER", targetId, targetLabel, params, null, null, null, null, null);
    }

    @Test
    void rendersFromStoredParamsInTheReadersLocale() {
        AuditRow export = row("AUDIT_EXPORT", ACTOR, "jperez", "Juan Pérez", null, null, "{\"detail\": 42}");
        assertThat(renderer.summary(export, ES)).isEqualTo("Juan Pérez exportó 42 eventos de la bitácora");
        assertThat(renderer.summary(export, Locale.ENGLISH)).contains("Juan Pérez").contains("42");
    }

    @Test
    void fallsBackToUsernameThenSystemOrUnknown() {
        assertThat(renderer.summary(row("AUDIT_EXPORT", ACTOR, "jperez", null, null, null, null), ES))
                .startsWith("jperez exportó");
        assertThat(renderer.summary(row("AUDIT_EXPORT", null, null, null, null, null, null), ES))
                .startsWith("Sistema exportó");
        assertThat(renderer.summary(row("AUDIT_EXPORT", ACTOR, null, null, null, null, null), ES))
                .startsWith("Alguien exportó");
    }

    @Test
    void targetUsesLabelThenId() {
        assertThat(renderer.summary(row("LOGIN_SUCCEEDED", ACTOR, "x", null, "u-1", "maria", null), ES))
                .isEqualTo("maria inició sesión");
        assertThat(renderer.summary(row("LOGIN_SUCCEEDED", ACTOR, "x", null, "u-1", null, null), ES))
                .isEqualTo("u-1 inició sesión");
    }

    @Test
    void unknownActionsFallBackToTheActionName() {
        assertThat(renderer.summary(row("SOMETHING_NEW", ACTOR, "x", null, null, null, null), ES)).isEqualTo("SOMETHING_NEW");
    }

    @Test
    void unreadableJsonIsTreatedAsEmpty() {
        assertThat(renderer.json("{not json")).isEmpty();
        assertThat(renderer.json(null)).isEmpty();
        assertThat(renderer.json("{\"a\":1}")).containsEntry("a", 1);
    }

    @Test
    void filterValidatesOrderAndMaximumRange() {
        Instant from = Instant.parse("2025-01-01T00:00:00Z");
        assertThatThrownBy(() -> filter(from, from.minusSeconds(1)).validate())
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.violations().getFirst().messageKey())
                        .isEqualTo("security.audit.rangeOrder"));
        assertThatThrownBy(() -> filter(from, from.plusSeconds(367L * 86400)).validate())
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.violations().getFirst().field()).isEqualTo("from");
                    assertThat(e.violations().getFirst().args()).containsExactly(366L);
                });
        filter(from, from.plusSeconds(366L * 86400)).validate();
    }

    @Test
    void shortSearchesAreIgnoredAndLikeWildcardsEscaped() {
        assertThat(new AuditEventFilter(" a ", null, null, null, null, null, null, null, null).search()).isNull();
        assertThat(AuditEventRepository.likeEscape("50%_off\\")).isEqualTo("50\\%\\_off\\\\");
    }

    private static AuditEventFilter filter(Instant from, Instant to) {
        return new AuditEventFilter(null, null, null, null, null, null, null, from, to);
    }
}
