package com.ninsky.cronos.kitchen.guide;

import java.math.BigDecimal;

/** Volume → mass for one ingredient (US cup = 236.6 ml); spoon values override cup/16 and cup/48 when set. */
public record IngredientConversion(String code, String name, BigDecimal gramsPerCup, BigDecimal gramsPerTablespoon,
                                   BigDecimal gramsPerTeaspoon) {
}
