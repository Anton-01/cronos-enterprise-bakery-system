package com.ninsky.cronos.domain.model.recipe;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/** Aggregate-internal child of {@link Recipe} — no independent repository/port exists for this today. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipeFixedCost {
    private UUID id;
    private String name;
    private String description;
    private String type;
    private BigDecimal rate;
    private String calculationMethod;
    private Integer timeInMinutes;
    private BigDecimal percentage;
    @Builder.Default
    private boolean isActive = true;
    private UUID masterFixedCostId;
    private BigDecimal calculatedAmount;
    @Builder.Default
    private Long version = 0L;
}
