package com.ninsky.cronos.application.service.impl;

import com.ninsky.cronos.application.service.UnitConversionService;
import com.ninsky.cronos.domain.entity.enums.UnitDimension;
import com.ninsky.cronos.domain.model.core.IngredientConversion;
import com.ninsky.cronos.domain.model.core.MeasurementUnit;
import com.ninsky.cronos.domain.model.core.UnitConversionResult;
import com.ninsky.cronos.domain.port.core.IngredientConversionRepositoryPort;
import com.ninsky.cronos.domain.port.core.MeasurementUnitRepositoryPort;
import com.ninsky.cronos.domain.port.core.UnitTypeRepositoryPort;
import com.ninsky.cronos.infrastructure.exception.CatalogException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * Single entry point for every quantity conversion (recipe costing, recipe sync, the on-the-fly
 * {@code POST /measurement-unit/convert}). Routing is decided by the {@link UnitDimension} of each
 * unit's type — never by free-text labels.
 */
@Service
@RequiredArgsConstructor
public class UnitConversionServiceImplementation implements UnitConversionService {

    /**
     * Intermediate precision. Matches {@code multiplier_to_base numeric(20,10)}: at the former
     * scale of 6, half a milligram expressed in kilograms (0.0000005) rounded to 0.000001 — a 100%
     * error on small additives (yeast, salt, colourants).
     */
    static final int MATH_SCALE = 10;

    private final IngredientConversionRepositoryPort ingredientConversionRepo;
    private final MeasurementUnitRepositoryPort measurementUnitRepository;
    private final UnitTypeRepositoryPort unitTypeRepository;

    @Override
    public BigDecimal convert(BigDecimal amount, MeasurementUnit fromUnit, MeasurementUnit toUnit, UUID ingredientId) {
        return convertWithTrace(amount, fromUnit, toUnit, ingredientId).quantity();
    }

    @Override
    public UnitConversionResult convertWithTrace(BigDecimal amount, MeasurementUnit fromUnit, MeasurementUnit toUnit, UUID ingredientId) {
        if (fromUnit.getId().equals(toUnit.getId())) {
            return UnitConversionResult.identity(amount == null ? BigDecimal.ZERO : amount);
        }

        UnitDimension fromDimension = dimensionOf(fromUnit);
        UnitDimension toDimension = dimensionOf(toUnit);

        if (fromDimension == toDimension) {
            return UnitConversionResult.linear(amount == null ? BigDecimal.ZERO : convertLinear(amount, fromUnit, toUnit));
        }
        if (!fromDimension.isDensityBridgeableWith(toDimension)) {
            throw CatalogException.businessRule("catalog.conversion.incompatible",
                    fromUnit.getCodeIdentity(), toUnit.getCodeIdentity(), fromDimension, toDimension);
        }

        IngredientConversion rule = densityRuleFor(ingredientId, fromDimension == UnitDimension.VOLUME ? fromUnit : toUnit);
        if (amount == null || amount.signum() == 0) {
            return UnitConversionResult.density(BigDecimal.ZERO, rule.getId());
        }
        MeasurementUnit ruleVolumeUnit = measurementUnitOf(rule.getVolumeUnitId());
        MeasurementUnit ruleMassUnit = measurementUnitOf(rule.getMassUnitId());

        BigDecimal converted = fromDimension == UnitDimension.VOLUME
                ? convertVolumeToMass(amount, fromUnit, toUnit, rule, ruleVolumeUnit, ruleMassUnit)
                : convertMassToVolume(amount, fromUnit, toUnit, rule, ruleVolumeUnit, ruleMassUnit);
        return UnitConversionResult.density(converted, rule.getId());
    }

    @Override
    public BigDecimal scaleQuantity(BigDecimal originalAmount, BigDecimal scaleFactor) {
        if (originalAmount == null || scaleFactor == null) return BigDecimal.ZERO;
        return originalAmount.multiply(scaleFactor).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal convertLinear(BigDecimal amount, MeasurementUnit fromUnit, MeasurementUnit toUnit) {
        BigDecimal baseAmount = amount.multiply(fromUnit.getMultiplierToBase());
        return baseAmount.divide(toUnit.getMultiplierToBase(), MATH_SCALE, RoundingMode.HALF_UP);
    }

    /** requested volume → rule volume → × factor → rule mass → requested mass. */
    private static BigDecimal convertVolumeToMass(BigDecimal volumeAmount, MeasurementUnit requestedVolumeUnit, MeasurementUnit requestedMassUnit,
                                                  IngredientConversion rule, MeasurementUnit ruleVolumeUnit, MeasurementUnit ruleMassUnit) {
        BigDecimal amountInRuleVolume = convertLinear(volumeAmount, requestedVolumeUnit, ruleVolumeUnit);
        BigDecimal massInRuleUnit = amountInRuleVolume.multiply(rule.getFactor());
        return convertLinear(massInRuleUnit, ruleMassUnit, requestedMassUnit);
    }

    /** requested mass → rule mass → ÷ factor → rule volume → requested volume. */
    private static BigDecimal convertMassToVolume(BigDecimal massAmount, MeasurementUnit requestedMassUnit, MeasurementUnit requestedVolumeUnit,
                                                  IngredientConversion rule, MeasurementUnit ruleVolumeUnit, MeasurementUnit ruleMassUnit) {
        if (rule.getFactor().signum() == 0) {
            throw CatalogException.businessRule("catalog.conversion.zeroDensity", rule.getId());
        }
        BigDecimal amountInRuleMass = convertLinear(massAmount, requestedMassUnit, ruleMassUnit);
        BigDecimal volumeInRuleUnit = amountInRuleMass.divide(rule.getFactor(), MATH_SCALE, RoundingMode.HALF_UP);
        return convertLinear(volumeInRuleUnit, ruleVolumeUnit, requestedVolumeUnit);
    }

    /**
     * An ingredient can carry one rule per volume unit (cup, tbsp, tsp). The rule measured in the
     * requested volume unit is preferred — it is the baker's own measurement, not a derived one;
     * otherwise the oldest rule is used, deterministically. (Previously a single-result lookup
     * that threw as soon as an ingredient had more than one rule.)
     */
    private IngredientConversion densityRuleFor(UUID ingredientId, MeasurementUnit requestedVolumeUnit) {
        List<IngredientConversion> rules = ingredientId == null ? List.of() : ingredientConversionRepo.findAllByIngredientId(ingredientId);
        return rules.stream()
                .filter(rule -> rule.getVolumeUnitId().equals(requestedVolumeUnit.getId()))
                .findFirst()
                .or(() -> rules.stream().findFirst())
                .orElseThrow(() -> CatalogException.businessRule("catalog.conversion.densityMissing"));
    }

    private UnitDimension dimensionOf(MeasurementUnit unit) {
        return unitTypeRepository.findById(unit.getUnitTypeId())
                .orElseThrow(() -> CatalogException.notFound("catalog.unitType.notFound", unit.getUnitTypeId()))
                .getDimension();
    }

    private MeasurementUnit measurementUnitOf(Long id) {
        return measurementUnitRepository.findById(id)
                .orElseThrow(() -> CatalogException.notFound("catalog.unit.notFound", id));
    }
}
