package com.ninsky.cronos.finance.settings;

import com.ninsky.cronos.finance.currency.CurrencyOption;
import com.ninsky.cronos.finance.pricing.FinanceRoundingMode;
import com.ninsky.cronos.finance.shared.UserRef;
import com.ninsky.cronos.finance.taxrate.TaxRateOption;

import java.time.Instant;

/** {@code GET /finance/settings} (spec §11.1). */
public record FinanceSettingsResponse(
        CurrencyOption defaultCurrency,
        TaxRateOption defaultTaxRate,
        boolean pricesIncludeTax,
        FinanceRoundingMode roundingMode,
        Instant updatedAt,
        UserRef updatedBy,
        Long version
) {
}
