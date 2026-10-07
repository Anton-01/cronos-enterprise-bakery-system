package com.ninsky.cronos.kitchen.recipe;

import com.ninsky.cronos.finance.shared.UserRef;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/** One history entry (§5.8). */
public record RecipeRevision(long version, Instant changedAt, UserRef changedBy, String summary, Map<String, Object> changes,
                             BigDecimal costPerUnit) {
}
