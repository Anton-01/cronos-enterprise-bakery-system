package com.ninsky.cronos.application.response.core;

import java.math.BigDecimal;

public record DensityConversionDto(
        BigDecimal gramsPerCup,
        BigDecimal gramsPerTablespoon,
        BigDecimal gramsPerTeaspoon
) { }
