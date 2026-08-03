package com.ninsky.cronos.application.service;

import com.ninsky.cronos.domain.entity.core.MeasurementUnit;

import java.math.BigDecimal;
import java.util.UUID;

public interface UnitConversionService {
    BigDecimal convert(BigDecimal amount, MeasurementUnit fromUnit, MeasurementUnit toUnit, UUID ingredientId);
    //BigDecimal convertVolumeToMass(BigDecimal volumeAmount, MeasurementUnit requestedVolumeUnit,
      //                             MeasurementUnit requestedMassUnit, UUID ingredientId);
    //BigDecimal convertMassToVolume(BigDecimal massAmount, MeasurementUnit requestedMassUnit,
        //                           MeasurementUnit requestedVolumeUnit, UUID ingredientId);
    BigDecimal scaleQuantity(BigDecimal originalAmount, BigDecimal scaleFactor);
}
