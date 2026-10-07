package com.ninsky.cronos.finance.shared;

import com.ninsky.cronos.infrastructure.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** Serialises default switches (both catalogs) on the single finance_settings row. */
@Component
@RequiredArgsConstructor
public class FinanceLocks {

    private final FinanceLockCustomRepository repository;

    /** Row lock held until the caller's transaction ends. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockDefaults() {
        repository.lockSettingsRow();
    }

    /** Stale {@code version} → 409 CONCURRENT_MODIFICATION (N3). */
    public static void requireVersion(Long requested, Long current) {
        if (!Objects.equals(requested, current)) {
            throw ApiException.concurrentModification();
        }
    }
}
