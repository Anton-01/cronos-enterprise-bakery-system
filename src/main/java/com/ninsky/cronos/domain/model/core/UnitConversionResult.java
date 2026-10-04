package com.ninsky.cronos.domain.model.core;

import java.math.BigDecimal;

/**
 * Outcome of a unit conversion plus how it was obtained, so a caller can show the user why a
 * number is what it is ({@code densityRuleId} is set only for {@link Path#DENSITY}).
 */
public record UnitConversionResult(BigDecimal quantity, Path path, Long densityRuleId) {

    public enum Path {
        /** Same unit: quantity returned untouched. */
        IDENTITY,
        /** Same dimension: through both units' {@code multiplierToBase}. */
        LINEAR,
        /** MASS ⇄ VOLUME: through the ingredient's density rule. */
        DENSITY
    }

    public static UnitConversionResult identity(BigDecimal quantity) {
        return new UnitConversionResult(quantity, Path.IDENTITY, null);
    }

    public static UnitConversionResult linear(BigDecimal quantity) {
        return new UnitConversionResult(quantity, Path.LINEAR, null);
    }

    public static UnitConversionResult density(BigDecimal quantity, Long densityRuleId) {
        return new UnitConversionResult(quantity, Path.DENSITY, densityRuleId);
    }
}
