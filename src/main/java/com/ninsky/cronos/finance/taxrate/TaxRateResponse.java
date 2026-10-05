package com.ninsky.cronos.finance.taxrate;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ninsky.cronos.finance.pricing.TaxFactorType;
import com.ninsky.cronos.finance.shared.FinanceStatus;
import com.ninsky.cronos.finance.shared.UserRef;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** IVA rate read model (spec §10.1). */
public record TaxRateResponse(
        Long id,
        String code,
        String name,
        String description,
        String satTaxCode,
        TaxFactorType factorType,
        BigDecimal ratePercent,
        LocalDate validFrom,
        LocalDate validTo,
        @JsonProperty("isDefault") boolean isDefault,
        @JsonProperty("inUse") boolean inUse,
        FinanceStatus status,
        Instant createdAt,
        Instant updatedAt,
        UserRef updatedBy,
        Long version
) {
}
