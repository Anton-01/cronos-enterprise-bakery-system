package com.ninsky.cronos.application.service;

import com.ninsky.cronos.domain.model.core.MeasurementUnit;
import com.ninsky.cronos.domain.model.core.UnitConversionResult;

import java.math.BigDecimal;
import java.util.UUID;

public interface UnitConversionService {

    /** Shorthand for {@link #convertWithTrace}{@code .quantity()} — what costing code needs. */
    BigDecimal convert(BigDecimal amount, MeasurementUnit fromUnit, MeasurementUnit toUnit, UUID ingredientId);

    /**
     * Converts {@code amount} from one unit to another. {@code ingredientId} is only consulted for
     * MASS ⇄ VOLUME (density) and may be {@code null} otherwise.
     *
     * @throws com.ninsky.cronos.infrastructure.exception.CatalogException when the dimensions
     *         cannot be bridged, or a density conversion has no rule for the ingredient
     */
    UnitConversionResult convertWithTrace(BigDecimal amount, MeasurementUnit fromUnit, MeasurementUnit toUnit, UUID ingredientId);

    BigDecimal scaleQuantity(BigDecimal originalAmount, BigDecimal scaleFactor);
}
