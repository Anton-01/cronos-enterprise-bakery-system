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
public class RecipeIngredient {
    private UUID id;
    private UUID rawMaterialId;
    private BigDecimal quantity;
    private Long unitId;
    private Integer displayOrder;
    @Builder.Default
    private boolean isOptional = false;
    private String notes;
    private BigDecimal costPerUnit;
    private BigDecimal totalCost;
    @Builder.Default
    private Long version = 0L;
}
