package com.ninsky.cronos.domain.model.recipe;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngredientSubstitute {
    private UUID id;
    private UUID userId;
    private UUID originalIngredientId;
    private UUID substituteMaterialId;
    @Builder.Default
    private BigDecimal conversionRatio = BigDecimal.ONE;
    private String reason;
    private String notes;
    @Builder.Default
    private Long version = 0L;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
