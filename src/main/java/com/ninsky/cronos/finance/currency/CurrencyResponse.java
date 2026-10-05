package com.ninsky.cronos.finance.currency;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ninsky.cronos.finance.shared.FinanceStatus;
import com.ninsky.cronos.finance.shared.UserRef;

import java.time.Instant;

/** Currency read model (spec §9.1). */
public record CurrencyResponse(
        Long id,
        String code,
        String numericCode,
        String name,
        String symbol,
        int decimalPlaces,
        SymbolPosition symbolPosition,
        @JsonProperty("isDefault") boolean isDefault,
        @JsonProperty("inUse") boolean inUse,
        FinanceStatus status,
        Instant createdAt,
        Instant updatedAt,
        UserRef updatedBy,
        Long version
) {
}
