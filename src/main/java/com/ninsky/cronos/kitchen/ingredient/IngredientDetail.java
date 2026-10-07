package com.ninsky.cronos.kitchen.ingredient;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.ninsky.cronos.finance.shared.UserRef;
import com.ninsky.cronos.kitchen.shared.AllergenRef;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** §4.1 detail = summary + editable fields, both prices, substitutes and detection suggestions. */
public record IngredientDetail(@JsonUnwrapped IngredientSummary summary, String description, String brand, BigDecimal densityGPerMl,
                               IngredientPrice referencePrice, IngredientPrice ownPrice, List<SubstituteResponse> substitutes,
                               List<AllergenRef> suggestedAllergens, Instant createdAt, Instant updatedAt, UserRef updatedBy,
                               long version) {
}
