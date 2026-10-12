package com.ninsky.cronos.kitchen.ingredient;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A purchase price as registered; {@code supplier} is serialized even when null. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record IngredientPrice(BigDecimal purchaseQuantity, long purchaseUnitId, String purchaseUnitCode, BigDecimal price,
                              String currency, String supplier, LocalDate pricedAt) {
}
