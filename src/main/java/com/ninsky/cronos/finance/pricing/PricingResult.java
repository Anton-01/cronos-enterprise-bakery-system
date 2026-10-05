package com.ninsky.cronos.finance.pricing;

import java.math.BigDecimal;
import java.util.List;

/** Document totals: {@code total = subtotal + tax + deliveryFee + extraFee}. */
public record PricingResult(List<LineAmounts> lines, BigDecimal subtotal, BigDecimal tax,
                            BigDecimal deliveryFee, BigDecimal extraFee, BigDecimal total) {

    public PricingResult {
        lines = List.copyOf(lines);
    }
}
