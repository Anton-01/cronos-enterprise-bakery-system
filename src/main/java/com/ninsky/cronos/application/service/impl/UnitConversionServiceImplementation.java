package com.ninsky.cronos.application.service.impl;

import com.ninsky.cronos.application.service.UnitConversionService;
import com.ninsky.cronos.domain.entity.core.IngredientConversion;
import com.ninsky.cronos.domain.entity.core.MeasurementUnit;
import com.ninsky.cronos.infrastructure.persistence.core.IngredientConversionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UnitConversionServiceImplementation implements UnitConversionService {
    private final IngredientConversionRepository ingredientConversionRepo;
    private static final int MATH_SCALE = 6;

    /**
     * MÉTODO FACHADA (PUNTO DE ENTRADA ÚNICO)
     * El sistema llama a este método y él decide qué ruta matemática tomar
     * basándose en la dimensión (MASA, VOLUMEN) de la tabla UnitType.
     */
    @Override
    public BigDecimal convert(BigDecimal amount, MeasurementUnit fromUnit, MeasurementUnit toUnit, UUID ingredientId) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        // 1. Si son exactamente la misma unidad, retornamos la cantidad intacta
        if (fromUnit.getId().equals(toUnit.getId())) {
            return amount;
        }

        // 2. Extraer las dimensiones usando tu modelo de BD
        String fromDimension = fromUnit.getUnitType().getDimension().toUpperCase();
        String toDimension = toUnit.getUnitType().getDimension().toUpperCase();

        // 3. Enrutamiento automático
        if (fromDimension.equals(toDimension)) {
            return convertWithinSameDimension(amount, fromUnit, toUnit);
        }

        if ("VOLUMEN".equals(fromDimension) && "MASA".equals(toDimension)) {
            return convertVolumeToMass(amount, fromUnit, toUnit, ingredientId);
        }

        if ("MASA".equals(fromDimension) && "VOLUMEN".equals(toDimension)) {
            return convertMassToVolume(amount, fromUnit, toUnit, ingredientId);
        }

        // Si intentan convertir "PIEZAS" (CONTEO) a "LITROS" sin una regla especial
        throw new IllegalArgumentException(
                "Conversión dimensional no soportada directamente entre " + fromDimension + " y " + toDimension +
                        ". Se requiere una regla de equivalencia específica."
        );
    }

    /**
     * 1. CONVERSIÓN LINEAL (Misma Dimensión)
     */

    private BigDecimal convertWithinSameDimension(BigDecimal amount, MeasurementUnit fromUnit, MeasurementUnit toUnit) {
        BigDecimal baseAmount = amount.multiply(fromUnit.getMultiplierToBase());
        return baseAmount.divide(toUnit.getMultiplierToBase(), MATH_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 2. DE VOLUMEN A MASA
     */
    private BigDecimal convertVolumeToMass(BigDecimal volumeAmount, MeasurementUnit requestedVolumeUnit,
                                           MeasurementUnit requestedMassUnit, UUID ingredientId) {

        IngredientConversion densityRule = getDensityRuleOrThrow(ingredientId);

        // A. Convertir al volumen de la regla
        BigDecimal amountInRuleVolume = convertWithinSameDimension(
                volumeAmount, requestedVolumeUnit, densityRule.getVolumeUnit());

        // B. Aplicar factor de equivalencia (Multiplicar)
        BigDecimal massInRuleUnit = amountInRuleVolume.multiply(densityRule.getFactor());

        // C. Convertir a la masa solicitada
        return convertWithinSameDimension(
                massInRuleUnit, densityRule.getMassUnit(), requestedMassUnit);
    }

    /**
     * 3. DE MASA A VOLUMEN
     */
    private BigDecimal convertMassToVolume(BigDecimal massAmount, MeasurementUnit requestedMassUnit,
                                           MeasurementUnit requestedVolumeUnit, UUID ingredientId) {

        IngredientConversion densityRule = getDensityRuleOrThrow(ingredientId);

        // A. Convertir a la masa de la regla
        BigDecimal amountInRuleMass = convertWithinSameDimension(
                massAmount, requestedMassUnit, densityRule.getMassUnit());

        // B. Aplicar densidad inversa (Dividir)
        if (densityRule.getFactor().compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalStateException("El factor de conversión culinaria no puede ser cero.");
        }
        BigDecimal volumeInRuleUnit = amountInRuleMass.divide(densityRule.getFactor(), MATH_SCALE, RoundingMode.HALF_UP);

        // C. Convertir al volumen solicitado
        return convertWithinSameDimension(
                volumeInRuleUnit, densityRule.getVolumeUnit(), requestedVolumeUnit);
    }

    /**
     * 4. ESCALADO DE RECETA
     */
    @Override
    public BigDecimal scaleQuantity(BigDecimal originalAmount, BigDecimal scaleFactor) {
        if (originalAmount == null || scaleFactor == null) return BigDecimal.ZERO;
        return originalAmount.multiply(scaleFactor).setScale(2, RoundingMode.HALF_UP);
    }

    private IngredientConversion getDensityRuleOrThrow(UUID ingredientId) {
        return ingredientConversionRepo.findByIngredientId(ingredientId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No es posible realizar la conversión. Falta configurar la regla de equivalencia (Masa/Volumen) para este ingrediente."
                ));
    }
}
