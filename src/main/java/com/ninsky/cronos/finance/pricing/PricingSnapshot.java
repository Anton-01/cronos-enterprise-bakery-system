package com.ninsky.cronos.finance.pricing;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Currency + tax + settings frozen on a document at creation (spec §11.4). Recalculations use it,
 * never the current defaults. {@code taxRateId} is null for a free-form rate.
 */
public record PricingSnapshot(String currencyCode, int currencyDecimalPlaces, Long taxRateId, TaxFactorType taxFactorType,
                              BigDecimal taxRatePercent, boolean pricesIncludeTax, FinanceRoundingMode roundingMode) {

    public PricingSnapshot {
        Objects.requireNonNull(currencyCode, "currencyCode");
        Objects.requireNonNull(taxFactorType, "taxFactorType");
        Objects.requireNonNull(roundingMode, "roundingMode");
        taxRatePercent = taxFactorType == TaxFactorType.EXENTO ? null : taxRatePercent;
    }

    public PricingRules rules() {
        return new PricingRules(currencyDecimalPlaces, taxFactorType, taxRatePercent, pricesIncludeTax, roundingMode);
    }

    /** The rate as stored in a NOT NULL column (EXENTO → 0). */
    public BigDecimal effectiveRatePercent() {
        return taxRatePercent == null ? BigDecimal.ZERO : taxRatePercent;
    }

    public PricingSnapshot withCurrency(String code, int decimalPlaces) {
        return new PricingSnapshot(code, decimalPlaces, taxRateId, taxFactorType, taxRatePercent, pricesIncludeTax, roundingMode);
    }

    public PricingSnapshot withTax(Long rateId, TaxFactorType factorType, BigDecimal ratePercent) {
        return new PricingSnapshot(currencyCode, currencyDecimalPlaces, rateId, factorType, ratePercent, pricesIncludeTax, roundingMode);
    }
}
