package com.ninsky.cronos.finance.pricing;

import java.math.BigDecimal;

/** One priced line: quantity × unit price (net or gross, per {@link PricingRules#pricesIncludeTax()}). */
public record PriceLine(BigDecimal quantity, BigDecimal unitPrice) {

    public PriceLine {
        PricingCalculator.requireNonNegative(quantity, "quantity");
        PricingCalculator.requireNonNegative(unitPrice, "unitPrice");
    }
}
