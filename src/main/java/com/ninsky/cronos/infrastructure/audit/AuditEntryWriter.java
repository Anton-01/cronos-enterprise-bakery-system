package com.ninsky.cronos.infrastructure.audit;

import com.ninsky.cronos.domain.model.audit.AuditLogEntry;
import com.ninsky.cronos.domain.port.audit.AuditLogPort;
import com.ninsky.cronos.eventing.application.EventEnvelope;
import com.ninsky.cronos.eventing.application.OutboxHandler;
import org.springframework.stereotype.Component;

/** Writes queued audit rows (outbox consumer; at most once per entry thanks to {@code processed_events}). */
@Component
public class AuditEntryWriter implements OutboxHandler<AuditEntryRecorded> {

    private final AuditLogPort auditLog;

    public AuditEntryWriter(AuditLogPort auditLog) {
        this.auditLog = auditLog;
    }

    @Override
    public Class<AuditEntryRecorded> eventClass() {
        return AuditEntryRecorded.class;
    }

    @Override
    public void handle(AuditEntryRecorded event, EventEnvelope envelope) {
        auditLog.save(AuditLogEntry.builder()
                .actorUserId(event.actorUserId())
                .actorUsername(event.actorUsername())
                .action(event.action())
                .targetType(event.targetType())
                .targetId(event.targetId())
                .details(event.details())
                .changes(event.changesJson())
                .ipAddress(event.ipAddress())
                .userAgent(event.userAgent())
                .traceId(event.traceId())
                .createdAt(event.occurredAt())
                .build());
    }

    @Override
    public String name() {
        return "AuditEntryWriter";
    }
}
