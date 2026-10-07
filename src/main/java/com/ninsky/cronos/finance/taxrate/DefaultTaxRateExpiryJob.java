package com.ninsky.cronos.finance.taxrate;

import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.domain.model.audit.AuditSeverity;
import com.ninsky.cronos.iam.audit.AuditEvent;
import com.ninsky.cronos.iam.audit.AuditLogCustomRepository;
import com.ninsky.cronos.iam.audit.AuditRecorder;
import com.ninsky.cronos.iam.audit.AuditTargets;
import com.ninsky.cronos.iam.shared.TenantTime;
import com.ninsky.cronos.infrastructure.persistence.lock.AdvisoryLockCustomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;

/**
 * Daily at 00:05 tenant time: an expired default tax rate stays the default (never switched
 * silently) but raises one FINANCE_DEFAULT_EXPIRED warning per day. Idempotent across instances
 * without ShedLock: an advisory lock serialises runs and a rate already reported today is skipped.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultTaxRateExpiryJob {

    private static final long JOB_LOCK_KEY = 7_426_101L;

    private final TaxRateRepository repository;
    private final AdvisoryLockCustomRepository locks;
    private final AuditLogCustomRepository auditLog;
    private final AuditRecorder audit;
    private final Clock clock;

    @Scheduled(cron = "0 5 0 * * *", zone = "America/Mexico_City")
    @Transactional(readOnly = true)
    public void run() {
        LocalDate today = TenantTime.today(clock);
        // Held until this transaction ends, after the independent audit insert has committed.
        locks.lockForTransaction(JOB_LOCK_KEY);
        repository.findByIsDefaultTrue()
                .filter(rate -> rate.getValidTo() != null && rate.getValidTo().isBefore(today))
                .filter(rate -> !alreadyReported(rate.getId(), today))
                .ifPresent(rate -> {
                    log.warn("Default tax rate {} expired on {}", rate.getCode(), rate.getValidTo());
                    audit.recordIndependently(AuditEvent.of(AuditAction.FINANCE_DEFAULT_EXPIRED, AuditTargets.TAX_RATE, rate.getId(),
                                    rate.getName() + " (" + rate.getCode() + ")")
                            .severity(AuditSeverity.WARNING)
                            .params(Map.of("detail", rate.getValidTo().toString()))
                            .build());
                });
    }

    /** audit_log.created_at holds tenant wall time. */
    private boolean alreadyReported(long rateId, LocalDate today) {
        return auditLog.existsForTargetSince(AuditAction.FINANCE_DEFAULT_EXPIRED.name(), AuditTargets.TAX_RATE, Long.toString(rateId),
                today.atStartOfDay());
    }
}
