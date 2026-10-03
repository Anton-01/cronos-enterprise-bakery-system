package com.ninsky.cronos.account.fiscal.application.port;

import com.ninsky.cronos.account.fiscal.domain.FiscalData;

import java.util.Optional;
import java.util.UUID;

public interface FiscalDataRepository {

    Optional<FiscalData> findByUserId(UUID userId);

    /**
     * Inserts when {@code data.version()} is null, otherwise updates the row expected to be at that
     * version (a concurrent writer surfaces as an optimistic-locking failure → 409).
     * @return the persisted state, with its new version and {@code updatedAt}
     */
    FiscalData save(FiscalData data);
}
