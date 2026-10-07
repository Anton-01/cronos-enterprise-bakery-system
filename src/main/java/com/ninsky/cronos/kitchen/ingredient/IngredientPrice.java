package com.ninsky.cronos.kitchen.ingredient;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A purchase price as registered. */
public record IngredientPrice(BigDecimal purchaseQuantity, long purchaseUnitId, String purchaseUnitCode, BigDecimal price,
                              String currency, String supplier, LocalDate pricedAt) {
}
