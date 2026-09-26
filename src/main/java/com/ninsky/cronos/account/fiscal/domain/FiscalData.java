package com.ninsky.cronos.account.fiscal.domain;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.SequencedMap;
import java.util.UUID;

/**
 * A user's fiscal identity (1:1 with the user). {@code taxpayerType} is never stored independently
 * of the RFC — it is always {@link Rfc#taxpayerType()}. {@code version}/{@code updatedAt} are null
 * until first persisted.
 */
public record FiscalData(
        UUID userId,
        LegalName legalName,
        Rfc rfc,
        TaxRegime taxRegime,
        FiscalAddress address,
        Long version,
        LocalDateTime updatedAt
) {
    public FiscalData {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(legalName, "legalName");
        Objects.requireNonNull(rfc, "rfc");
        Objects.requireNonNull(taxRegime, "taxRegime");
        Objects.requireNonNull(address, "address");
    }

    public TaxpayerType taxpayerType() {
        return rfc.taxpayerType();
    }

    /** Flat, JSON-path-keyed view used for field-level audit diffs. */
    public SequencedMap<String, Object> snapshot() {
        SequencedMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("legalName", legalName.value());
        snapshot.put("taxId", rfc.value());
        snapshot.put("taxpayerType", taxpayerType().name());
        snapshot.put("taxRegime", taxRegime.code());
        snapshot.put("address.street", address.street());
        snapshot.put("address.exteriorNumber", address.exteriorNumber());
        snapshot.put("address.interiorNumber", address.interiorNumber());
        snapshot.put("address.neighborhood", address.neighborhood());
        snapshot.put("address.municipality", address.municipality());
        snapshot.put("address.state", address.state().code());
        snapshot.put("address.zipCode", address.zipCode().value());
        snapshot.put("address.country", address.country());
        return snapshot;
    }
}
