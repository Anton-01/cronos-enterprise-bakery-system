package com.ninsky.cronos.finance.pricing;

import java.math.RoundingMode;

/** Rounding modes a tenant may choose (spec §11.1); a closed subset of {@link RoundingMode}. */
public enum FinanceRoundingMode {
    HALF_UP(RoundingMode.HALF_UP),
    HALF_EVEN(RoundingMode.HALF_EVEN),
    UP(RoundingMode.UP),
    DOWN(RoundingMode.DOWN);

    private final RoundingMode javaMode;

    FinanceRoundingMode(RoundingMode javaMode) {
        this.javaMode = javaMode;
    }

    public RoundingMode toJava() {
        return javaMode;
    }
}
