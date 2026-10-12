package com.ninsky.cronos.infrastructure.audit;

import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.eventing.domain.DomainEvent;
import com.ninsky.cronos.eventing.domain.EventType;
import com.ninsky.cronos.eventing.domain.Sensitive;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * An audit row to write (account and catalog trails). It travels through the outbox, written in the transaction of
 * the audited change, so a crash after commit no longer loses the row (the former {@code @Async} after-commit writer
 * did). The masked diff and the client's address are encrypted at rest in the outbox.
 */
@EventType("audit.entry-recorded")
public record AuditEntryRecorded(UUID entryId, UUID actorUserId, String actorUsername, AuditAction action, String targetType,
                                 String targetId, String details, @Sensitive String changesJson, @Sensitive String ipAddress,
                                 @Sensitive String userAgent, String traceId, LocalDateTime occurredAt) implements DomainEvent {

    @Override
    public String aggregateType() {
        return "AUDIT";
    }

    @Override
    public UUID aggregateId() {
        return entryId;
    }
}
