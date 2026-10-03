package com.ninsky.cronos.account.fiscal.application;

import com.ninsky.cronos.account.fiscal.application.port.FiscalDataRepository;
import com.ninsky.cronos.account.fiscal.domain.FiscalData;
import com.ninsky.cronos.account.fiscal.domain.UpsertFiscalDataCommand;
import com.ninsky.cronos.account.shared.application.port.AccountRateLimiter;
import com.ninsky.cronos.account.shared.application.port.AccountRateLimiter.RateLimitedAction;
import com.ninsky.cronos.account.shared.application.port.AuditTrail;
import com.ninsky.cronos.account.shared.application.port.CurrentUserProvider;
import com.ninsky.cronos.account.shared.domain.AccountDomainError;
import com.ninsky.cronos.account.shared.domain.AccountDomainError.VersionMismatch;
import com.ninsky.cronos.account.shared.domain.AccountDomainError.WriteRateLimited;
import com.ninsky.cronos.account.shared.domain.AccountDomainException;
import com.ninsky.cronos.account.shared.domain.PiiMasker;
import com.ninsky.cronos.account.shared.domain.audit.AuditChange;
import com.ninsky.cronos.account.shared.domain.audit.AuditDiff;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** PUT /users/me/fiscal — create on first call, full replace afterwards. */
@Slf4j
@Service
public class UpsertMyFiscalDataUseCase {

    private final CurrentUserProvider currentUserProvider;
    private final FiscalDataRepository fiscalDataRepository;
    private final List<FiscalRule> rules;
    private final AccountRateLimiter rateLimiter;
    private final AuditTrail auditTrail;
    private final PiiMasker piiMasker;

    /** {@code rules} arrives sorted by {@code @Order} (Spring sorts injected lists of ordered beans). */
    public UpsertMyFiscalDataUseCase(CurrentUserProvider currentUserProvider, FiscalDataRepository fiscalDataRepository,
                                     List<FiscalRule> rules, AccountRateLimiter rateLimiter, AuditTrail auditTrail,
                                     PiiMasker piiMasker) {
        this.currentUserProvider = currentUserProvider;
        this.fiscalDataRepository = fiscalDataRepository;
        this.rules = List.copyOf(rules);
        this.rateLimiter = rateLimiter;
        this.auditTrail = auditTrail;
        this.piiMasker = piiMasker;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public FiscalData execute(UpsertFiscalDataCommand command) {
        UUID userId = currentUserProvider.currentUserId();

        var decision = rateLimiter.tryConsume(userId, RateLimitedAction.FISCAL_WRITE);
        if (!decision.allowed()) {
            throw new AccountDomainException(WriteRateLimited.of(decision.retryAfterSeconds()));
        }

        List<AccountDomainError> violations = rules.stream()
                .flatMap(rule -> rule.check(command).stream())
                .toList();
        if (!violations.isEmpty()) {
            throw new AccountDomainException(violations);
        }

        Optional<FiscalData> existing = fiscalDataRepository.findByUserId(userId);
        Long currentVersion = existing.map(FiscalData::version).orElse(null);
        if (!command.expectedVersion().matches(currentVersion)) {
            throw new AccountDomainException(VersionMismatch.of());
        }

        FiscalData saved = fiscalDataRepository.save(command.toFiscalData(userId, currentVersion, null));

        var diff = AuditDiff.between(existing.<Map<String, Object>>map(FiscalData::snapshot).orElseGet(Map::of),
                saved.snapshot(), piiMasker);
        AuditChange change = existing.isPresent()
                ? new AuditChange.FiscalDataUpdated(userId, userId, diff)
                : new AuditChange.FiscalDataCreated(userId, userId, diff);
        if (existing.isEmpty() || !diff.isEmpty()) {
            auditTrail.record(change);
        }
        log.info("Fiscal data {} for user {} (fields changed: {})",
                existing.isPresent() ? "updated" : "created", userId, diff.keySet());
        return saved;
    }
}
