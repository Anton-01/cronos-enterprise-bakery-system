package com.ninsky.cronos.kitchen.costing;

import java.util.Optional;

/** How a fixed cost is charged to a batch (§5.5). */
public enum FixedCostMethod {
    HOURLY_RATE,
    PER_UNIT,
    FIXED_PER_BATCH,
    PERCENTAGE;

    public static Optional<FixedCostMethod> parse(String value) {
        try {
            return Optional.ofNullable(value).map(String::strip).map(FixedCostMethod::valueOf);
        } catch (IllegalArgumentException unknown) {
            return Optional.empty();
        }
    }
}
