package com.ninsky.cronos.kitchen.ingredient;

import com.ninsky.cronos.kitchen.shared.Dimension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** {@code POST/PUT /ingredients}; validated as a whole by {@link IngredientValidator}. */
public record IngredientRequest(String code, String name, Long categoryId, String description, String brand, Dimension baseDimension,
                                BigDecimal yieldPercent, BigDecimal densityGPerMl, List<Long> allergenIds, List<Substitute> substitutes,
                                IngredientPriceRequest price, Long version) {

    public IngredientRequest {
        allergenIds = allergenIds == null ? List.of() : allergenIds;
        substitutes = substitutes == null ? List.of() : substitutes;
    }

    public record Substitute(UUID ingredientId, BigDecimal ratio, String notes) {
    }
}
