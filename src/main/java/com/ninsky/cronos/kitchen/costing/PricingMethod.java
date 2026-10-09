package com.ninsky.cronos.kitchen.costing;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * How {@code targetMarginPercent} turns a unit cost into a suggested price (baking-studio §5.2).
 * <pre>
 * MARKUP: price = cost × (1 + p/100)    the profit is p % of the cost (every recipe before §5)
 * MARGIN: price = cost ÷ (1 − p/100)    the profit is p % of the price; p &lt; 100
 * </pre>
 */
public enum PricingMethod {
    MARKUP,
    MARGIN;

    /** Inclusive upper bound of {@code targetMarginPercent} under MARKUP. */
    public static final BigDecimal MAX_MARKUP_PERCENT = BigDecimal.valueOf(1000);
    /** Exclusive upper bound under MARGIN (the divisor reaches zero at 100 %). */
    public static final BigDecimal MAX_MARGIN_PERCENT = BigDecimal.valueOf(100);

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** Unrounded suggested price; {@code scale} digits of internal precision. */
    public BigDecimal price(BigDecimal cost, BigDecimal percent, int scale, RoundingMode mode) {
        BigDecimal ratio = percent.divide(HUNDRED, scale, mode);
        return switch (this) {
            case MARKUP -> cost.multiply(BigDecimal.ONE.add(ratio));
            case MARGIN -> cost.divide(BigDecimal.ONE.subtract(ratio), scale, mode);
        };
    }

    /**
     * Achieved percentage at {@code price} in this method's terms (comparable with {@code targetMarginPercent}):
     * MARKUP (price − cost) / cost, MARGIN (price − cost) / price; 1 decimal. Null when undefined.
     */
    public BigDecimal achieved(BigDecimal price, BigDecimal cost) {
        if (price == null || cost == null) {
            return null;
        }
        BigDecimal base = this == MARKUP ? cost : price;
        if (base.signum() == 0) {
            return null;
        }
        return price.subtract(cost).multiply(HUNDRED).divide(base, 1, RoundingMode.HALF_EVEN);
    }

    /** Whether {@code percent} is a usable target under this method. */
    public boolean accepts(BigDecimal percent) {
        if (percent == null || percent.signum() < 0) {
            return false;
        }
        return this == MARKUP ? percent.compareTo(MAX_MARKUP_PERCENT) <= 0 : percent.compareTo(MAX_MARGIN_PERCENT) < 0;
    }

    public static PricingMethod orDefault(PricingMethod method) {
        return method == null ? MARKUP : method;
    }
}
