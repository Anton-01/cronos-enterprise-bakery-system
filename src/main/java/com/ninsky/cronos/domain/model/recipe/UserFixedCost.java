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
public class UserFixedCost {
    private UUID id;
    private UUID userId;
    private String name;
    private String description;
    private String type;
    private BigDecimal defaultAmount;
    @Builder.Default
    private BigDecimal percentage = BigDecimal.ZERO;
    private String calculationMethod;
    @Builder.Default
    private boolean isActive = true;
    private boolean appliesByDefault;
    private BigDecimal monthlyAmount;
    private BigDecimal monthlyBasis;
    private String seedCode;
    @Builder.Default
    private Long version = 0L;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
