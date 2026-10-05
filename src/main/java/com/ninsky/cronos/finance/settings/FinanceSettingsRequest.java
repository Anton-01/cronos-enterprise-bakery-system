package com.ninsky.cronos.finance.settings;

import com.ninsky.cronos.finance.pricing.FinanceRoundingMode;
import jakarta.validation.constraints.NotNull;

/** {@code PUT /finance/settings}; defaults change only through the catalogs' {@code PATCH …/default}. */
public record FinanceSettingsRequest(
        @NotNull(message = "{api.validation.required}") Boolean pricesIncludeTax,
        @NotNull(message = "{api.validation.required}") FinanceRoundingMode roundingMode,
        @NotNull(message = "{api.validation.required}") Long version
) {
}
