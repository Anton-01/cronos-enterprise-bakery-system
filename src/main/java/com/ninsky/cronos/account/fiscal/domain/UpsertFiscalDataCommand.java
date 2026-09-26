package com.ninsky.cronos.account.fiscal.domain;

import com.ninsky.cronos.account.shared.domain.ExpectedVersion;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Fully-typed upsert intent. By the time this exists every value object has self-validated. No user
 * id: the use case takes identity from {@code CurrentUserProvider} only.
 */
public record UpsertFiscalDataCommand(
        LegalName legalName,
        Rfc rfc,
        TaxRegime taxRegime,
        FiscalAddress address,
        ExpectedVersion expectedVersion
) {
    public UpsertFiscalDataCommand {
        Objects.requireNonNull(legalName, "legalName");
        Objects.requireNonNull(rfc, "rfc");
        Objects.requireNonNull(taxRegime, "taxRegime");
        Objects.requireNonNull(address, "address");
        expectedVersion = expectedVersion == null ? ExpectedVersion.ANY : expectedVersion;
    }

    public TaxpayerIdentity identity() {
        return rfc.identity();
    }

    public FiscalData toFiscalData(UUID userId, Long version, LocalDateTime updatedAt) {
        return new FiscalData(userId, legalName, rfc, taxRegime, address, version, updatedAt);
    }
}
