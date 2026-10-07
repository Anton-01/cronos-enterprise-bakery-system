package com.ninsky.cronos.kitchen.ingredient;

import com.ninsky.cronos.finance.shared.UserRef;
import com.ninsky.cronos.kitchen.costing.PriceSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One row of {@code GET /ingredients/{id}/prices}: own prices and reference changes, newest first. */
public record PriceHistoryEntry(UUID id, PriceSource source, BigDecimal purchaseQuantity, long purchaseUnitId, String purchaseUnitCode,
                                BigDecimal price, String currency, String supplier, LocalDate pricedAt, BigDecimal costPerBaseUnit,
                                Instant recordedAt, UserRef recordedBy) {
}
