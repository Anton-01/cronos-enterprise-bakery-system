package com.ninsky.cronos.infrastructure.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ninsky.cronos.application.event.CatalogAuditEvent;
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

/**
 * Persists {@link CatalogAuditEvent}s once the business transaction has committed, off the request
 * thread. Same contract as the account module's {@code AuditLogWriter}: a failure is logged, never
 * propagated (the change already committed), and {@code REQUIRES_NEW} keeps the write committing
 * even when the listener runs synchronously (async disabled, tests).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogAuditLogWriter {

    private final AuditLogPort auditLogPort;
    private final ObjectMapper objectMapper;

    @Async("systemTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(CatalogAuditEvent event) {
        try {
            auditLogPort.save(AuditLogEntry.builder()
                    .actorUserId(event.actor().userId())
                    .actorUsername(event.actor().username())
                    .action(event.action())
                    .targetType(event.targetType())
                    .targetId(event.targetId())
                    .details(event.details())
                    .changes(event.changes().isEmpty() ? null : objectMapper.writeValueAsString(event.changes()))
                    .ipAddress(event.ipAddress())
                    .userAgent(event.userAgent())
                    .traceId(event.traceId())
                    .createdAt(event.occurredAt())
                    .build());
        } catch (JsonProcessingException | RuntimeException e) {
            log.error("Failed to write {} audit entry for {}/{} (traceId={}): {}",
                    event.action(), event.targetType(), event.targetId(), event.traceId(), e.getMessage());
        }
    }
}
