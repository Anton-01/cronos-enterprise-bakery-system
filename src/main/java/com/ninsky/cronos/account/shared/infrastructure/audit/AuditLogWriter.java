package com.ninsky.cronos.account.shared.infrastructure.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ninsky.cronos.account.shared.domain.audit.AuditChange;
import com.ninsky.cronos.account.shared.domain.audit.ResourceType;
import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.domain.model.audit.AuditLogEntry;
import com.ninsky.cronos.domain.port.audit.AuditLogPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Persists account audit events to the existing immutable {@code audit_log} ledger, only once the
 * business transaction has committed and off the request thread. A failure here is logged, never
 * propagated: the user's change already committed and must not be reported as failed.
 * <p>
 * {@code REQUIRES_NEW} matters even though the listener is {@code @Async}: if it ever runs
 * synchronously (async disabled, tests), an AFTER_COMMIT write would otherwise join the
 * already-committed transaction and never be committed itself.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogWriter {

    private final AuditLogPort auditLogPort;
    private final ObjectMapper objectMapper;

    @Async("systemTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(AuditRecorded event) {
        writeAll(List.of(event));
    }

    public void writeAll(List<AuditRecorded> events) {
        Map<ResourceType, List<AuditRecorded>> byResource = events.stream()
                .collect(Collectors.groupingBy(e -> e.change().resourceType(), () -> new EnumMap<>(ResourceType.class), Collectors.toList()));

        byResource.forEach((resourceType, batch) -> {
            batch.forEach(this::writeOne);
            log.debug("Wrote {} {} audit entr{}", batch.size(), resourceType, batch.size() == 1 ? "y" : "ies");
        });
    }

    private void writeOne(AuditRecorded event) {
        AuditChange change = event.change();
        try {
            auditLogPort.save(AuditLogEntry.builder()
                    .actorUserId(change.actorId())
                    .action(actionOf(change))
                    .targetType(change.resourceType().name())
                    .targetId(change.resourceId() == null ? null : change.resourceId().toString())
                    .changes(change.changes().isEmpty() ? null : objectMapper.writeValueAsString(change.changes()))
                    .ipAddress(truncate(event.ipAddress(), 45))
                    .userAgent(event.userAgent())
                    .traceId(event.traceId())
                    .createdAt(event.occurredAt())
                    .build());
        } catch (JsonProcessingException | RuntimeException e) {
            log.error("Failed to write {} audit entry for user {} (traceId={}): {}",
                    change.getClass().getSimpleName(), change.actorId(), event.traceId(), e.getMessage());
        }
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
