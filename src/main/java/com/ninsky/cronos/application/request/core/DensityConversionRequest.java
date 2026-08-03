package com.ninsky.cronos.application.request.core;

import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record DensityConversionRequest(

        @Positive(message = "Los gramos por taza deben ser mayores a cero.")
        BigDecimal gramsPerCup,

        @Positive(message = "Los gramos por cucharada deben ser mayores a cero.")
        BigDecimal gramsPerTablespoon,

        @Positive(message = "Los gramos por cucharadita deben ser mayores a cero.")
        BigDecimal gramsPerTeaspoon
) {}
