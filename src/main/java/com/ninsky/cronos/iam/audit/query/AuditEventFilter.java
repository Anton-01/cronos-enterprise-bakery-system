package com.ninsky.cronos.iam.audit.query;

import com.ninsky.cronos.domain.model.audit.AuditCategory;
import com.ninsky.cronos.domain.model.audit.AuditOutcome;
import com.ninsky.cronos.domain.model.audit.AuditSeverity;
import com.ninsky.cronos.iam.shared.Texts;
import com.ninsky.cronos.infrastructure.exception.Violations;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Filters of {@code GET /iam/audit-events} and its export (spec §7.3). */
public record AuditEventFilter(
        String search,
        List<AuditCategory> categories,
        List<AuditOutcome> outcomes,
        List<AuditSeverity> severities,
        UUID actorId,
        String targetType,
        String targetId,
        Instant from,
        Instant to
) {
    public static final Duration MAX_RANGE = Duration.ofDays(366);
    static final int MIN_SEARCH = 2;

    public AuditEventFilter {
        search = Optional.ofNullable(Texts.clean(search)).filter(s -> s.length() >= MIN_SEARCH).orElse(null);
        categories = categories == null ? List.of() : List.copyOf(categories);
        outcomes = outcomes == null ? List.of() : List.copyOf(outcomes);
        severities = severities == null ? List.of() : List.copyOf(severities);
        targetType = Texts.clean(targetType);
        targetId = Texts.clean(targetId);
    }

    /** {@code from ≤ to} and at most 366 days apart; both reported on {@code from}. */
    public void validate() {
        Violations violations = new Violations();
        if (from != null && to != null) {
            violations.invalidIf(from.isAfter(to), "from", "security.audit.rangeOrder")
                    .invalidIf(!from.isAfter(to) && Duration.between(from, to).compareTo(MAX_RANGE) > 0,
                            "from", "security.audit.rangeTooLong", MAX_RANGE.toDays());
        }
        violations.throwIfAny();
    }

    /** Short description stored with the export audit event. */
    public String summary() {
        return "search=%s categories=%s outcomes=%s severities=%s actorId=%s target=%s:%s from=%s to=%s".formatted(
                search, categories, outcomes, severities, actorId, targetType, targetId, from, to);
    }
}
