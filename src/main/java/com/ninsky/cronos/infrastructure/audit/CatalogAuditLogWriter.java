package com.ninsky.cronos.infrastructure.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ninsky.cronos.application.event.CatalogAuditEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Turns a catalog audit event into an outbox entry in the same transaction as the catalog change (written later by
 * {@link AuditEntryWriter}); a rolled-back change leaves no row, a crash after commit no longer loses one.
 */
@Slf4j
@Component
public class CatalogAuditLogWriter {

    private final AuditEntries entries;
    private final ObjectMapper objectMapper;

    public CatalogAuditLogWriter(AuditEntries entries, ObjectMapper objectMapper) {
        this.entries = entries;
        this.objectMapper = objectMapper;
    }

    @EventListener
    public void on(CatalogAuditEvent event) {
        String changes;
        try {
            changes = event.changes().isEmpty() ? null : objectMapper.writeValueAsString(event.changes());
        } catch (JsonProcessingException e) {
            log.error("Audit diff of {} {}/{} cannot be serialised (traceId={}): {}", event.action(), event.targetType(),
                    event.targetId(), event.traceId(), e.getMessage());
            changes = null;
        }
        entries.publish(new AuditEntryRecorded(entries.nextId(), event.actor().userId(), event.actor().username(), event.action(),
                event.targetType(), event.targetId(), event.details(), changes, event.ipAddress(), event.userAgent(), event.traceId(),
                event.occurredAt()));
    }
}
