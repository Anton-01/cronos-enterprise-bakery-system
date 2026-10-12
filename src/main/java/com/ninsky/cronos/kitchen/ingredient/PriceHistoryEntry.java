package com.ninsky.cronos.kitchen.ingredient;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ninsky.cronos.finance.shared.UserRef;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One row of {@code GET /ingredients/{id}/prices}: own prices and reference changes, newest first.
 * {@code changePercent} compares {@code costPerBaseUnit} with the previous (older) entry of the same series
 * (own or reference) and is {@code null} only for the oldest entry; it is always serialized.
 * {@code recordedBy} is {@code null} for system/seed reference prices.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record PriceHistoryEntry(UUID id, IngredientPrice price, BigDecimal costPerBaseUnit, BigDecimal changePercent,
                                Instant recordedAt, UserRef recordedBy) {
}
