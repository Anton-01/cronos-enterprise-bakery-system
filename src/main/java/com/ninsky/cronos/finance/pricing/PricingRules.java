package com.ninsky.cronos.finance.pricing;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Everything a calculation depends on: currency scale, tax factor/rate and tenant settings. A
 * document stores these as its snapshot so recalculations never read the current defaults.
 */
public record PricingRules(int decimalPlaces, TaxFactorType factorType, BigDecimal ratePercent,
                           boolean pricesIncludeTax, FinanceRoundingMode roundingMode) {

    public static final int MAX_DECIMAL_PLACES = 4;
    public static final int MAX_RATE_SCALE = 4;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public PricingRules {
        Objects.requireNonNull(factorType, "factorType");
        Objects.requireNonNull(roundingMode, "roundingMode");
        if (decimalPlaces < 0 || decimalPlaces > MAX_DECIMAL_PLACES) {
            throw new IllegalArgumentException("decimalPlaces must be between 0 and " + MAX_DECIMAL_PLACES);
        }
        if (factorType == TaxFactorType.EXENTO) {
            ratePercent = null;
        } else if (!isValidRate(ratePercent)) {
            throw new IllegalArgumentException("A TASA rate must be between 0 and 100 with at most 4 decimals");
        }
    }

    /** 0 ≤ rate ≤ 100 with scale ≤ 4 (NUMERIC(7,4)). */
    public static boolean isValidRate(BigDecimal ratePercent) {
        return ratePercent != null && ratePercent.signum() >= 0 && ratePercent.compareTo(HUNDRED) <= 0
                && ratePercent.stripTrailingZeros().scale() <= MAX_RATE_SCALE;
    }

    /** {@code ratePercent / 100}; EXENTO is 0. Exact (a decimal shift). */
    public BigDecimal rate() {
        return ratePercent == null ? BigDecimal.ZERO : ratePercent.movePointLeft(2);
    }

    /** The rate stored on documents whose column is NOT NULL: EXENTO is 0. */
    public BigDecimal effectiveRatePercent() {
        return ratePercent == null ? BigDecimal.ZERO : ratePercent;
    }
}
