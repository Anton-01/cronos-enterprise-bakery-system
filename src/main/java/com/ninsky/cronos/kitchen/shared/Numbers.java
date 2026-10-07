package com.ninsky.cronos.kitchen.shared;

import java.math.BigDecimal;

/** BigDecimal range/scale checks of the kitchen validators. */
public final class Numbers {

    private Numbers() {
    }

    public static boolean within(BigDecimal value, String min, String max, int maxScale) {
        return value != null && value.compareTo(new BigDecimal(min)) >= 0 && value.compareTo(new BigDecimal(max)) <= 0
                && scale(value) <= maxScale;
    }

    /** {@code > 0}, ≤ max, scale ≤ maxScale. */
    public static boolean positive(BigDecimal value, String max, int maxScale) {
        return value != null && value.signum() > 0 && value.compareTo(new BigDecimal(max)) <= 0 && scale(value) <= maxScale;
    }

    public static boolean within(Integer value, int min, int max) {
        return value == null || (value >= min && value <= max);
    }

    private static int scale(BigDecimal value) {
        return Math.max(0, value.stripTrailingZeros().scale());
    }
}
