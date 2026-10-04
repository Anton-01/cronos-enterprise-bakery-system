package com.ninsky.cronos.domain.entity.enums;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Physical dimension a {@code UnitType} measures. Drives conversion routing in
 * {@code UnitConversionService}: units of the same dimension convert linearly through their
 * {@code multiplierToBase}; MASS ⇄ VOLUME additionally needs a per-ingredient density rule.
 * <p>
 * Stored as the enum name ({@code unit_types.dimension}, CHECK-constrained in V8). Temperature is
 * deliberately absent: it is affine (°F = °C × 9/5 + 32), not proportional, so it cannot be
 * modelled with a single multiplier and would silently produce wrong numbers.
 */
public enum UnitDimension {
    MASS,
    VOLUME,
    COUNT,
    LENGTH;

    /** Case-insensitive, whitespace-tolerant lookup by enum name; empty for anything else. */
    public static Optional<UnitDimension> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(d -> d.name().equals(normalized)).findFirst();
    }

    /** Only these two dimensions can be bridged, and only through an ingredient density rule. */
    public boolean isDensityBridgeableWith(UnitDimension other) {
        return (this == MASS && other == VOLUME) || (this == VOLUME && other == MASS);
    }
}
