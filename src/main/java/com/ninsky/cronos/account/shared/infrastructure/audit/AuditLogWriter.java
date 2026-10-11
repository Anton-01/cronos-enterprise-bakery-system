package com.ninsky.cronos.account.shared.infrastructure.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ninsky.cronos.account.shared.domain.audit.AuditChange;
import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.infrastructure.audit.AuditEntries;
import com.ninsky.cronos.infrastructure.audit.AuditEntryRecorded;
import com.ninsky.cronos.infrastructure.audit.AuditEntryWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Queues account audit events for the immutable {@code audit_log} ledger: the entry enters the outbox in the
 * transaction of the audited change (rolled back with it) and {@link AuditEntryWriter} writes it after commit, with
 * retries. Before, an {@code @Async} after-commit listener lost the row when the process stopped in between.
 */
@Slf4j
@Component
public class AuditLogWriter {

    private final AuditEntries entries;
    private final ObjectMapper objectMapper;

    public AuditLogWriter(AuditEntries entries, ObjectMapper objectMapper) {
        this.entries = entries;
        this.objectMapper = objectMapper;
    }

    @EventListener
    public void on(AuditRecorded event) {
        AuditChange change = event.change();
        String changes;
        try {
            changes = change.changes().isEmpty() ? null : objectMapper.writeValueAsString(change.changes());
        } catch (JsonProcessingException e) {
            log.error("Audit diff of {} for user {} cannot be serialised (traceId={}): {}", change.getClass().getSimpleName(),
                    change.actorId(), event.traceId(), e.getMessage());
            changes = null;
        }
        entries.publish(new AuditEntryRecorded(entries.nextId(), change.actorId(), null, actionOf(change), change.resourceType().name(),
                change.resourceId() == null ? null : change.resourceId().toString(), null, changes, truncate(event.ipAddress(), 45),
                event.userAgent(), event.traceId(), event.occurredAt()));
    }

    static AuditAction actionOf(AuditChange change) {
        return switch (change) {
            case AuditChange.ProfileChanged ignored -> AuditAction.PROFILE_CHANGED;
            case AuditChange.AvatarChanged ignored -> AuditAction.AVATAR_CHANGED;
            case AuditChange.AvatarRemoved ignored -> AuditAction.AVATAR_REMOVED;
            case AuditChange.FiscalDataCreated ignored -> AuditAction.FISCAL_DATA_CREATED;
            case AuditChange.FiscalDataUpdated ignored -> AuditAction.FISCAL_DATA_UPDATED;
            case AuditChange.PasswordChanged ignored -> AuditAction.PASSWORD_CHANGED;
        };
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
