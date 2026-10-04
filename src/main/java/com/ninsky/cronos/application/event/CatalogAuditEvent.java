package com.ninsky.cronos.application.event;

import com.ninsky.cronos.domain.model.audit.Actor;
import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.domain.model.audit.FieldChange;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.SequencedMap;

/**
 * A master-data change plus the request context captured on the request thread (the after-commit
 * writer runs on a pool thread where the servlet request is gone). Published through
 * {@link com.ninsky.cronos.application.service.audit.CatalogAuditTrail}; persisted only if the
 * business transaction commits.
 */
public record CatalogAuditEvent(
        AuditAction action,
        String targetType,
        String targetId,
        Actor actor,
        SequencedMap<String, FieldChange> changes,
        String details,
        LocalDateTime occurredAt,
        String ipAddress,
        String userAgent,
        String traceId
) {
    public CatalogAuditEvent {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(targetType, "targetType");
        Objects.requireNonNull(actor, "actor");
        changes = Collections.unmodifiableSequencedMap(changes == null ? new LinkedHashMap<>() : new LinkedHashMap<>(changes));
    }
}
