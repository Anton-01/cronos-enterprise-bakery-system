package com.ninsky.cronos.kitchen.ingredient;

import java.math.BigDecimal;
import java.time.LocalDate;

/** {@code POST /ingredients/{id}/prices} (§4.4). */
public record IngredientPriceRequest(BigDecimal purchaseQuantity, Long purchaseUnitId, BigDecimal price, String currency, String supplier,
                                     LocalDate pricedAt) {
}
