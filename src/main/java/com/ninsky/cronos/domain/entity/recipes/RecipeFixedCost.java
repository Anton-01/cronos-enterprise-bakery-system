package com.ninsky.cronos.domain.entity.recipes;

import com.ninsky.cronos.domain.entity.base.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "recipe_fixed_costs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecipeFixedCost extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, length = 50)
    private String type; // Ej: MANUAL_LABOR, PACKAGING, OVERHEAD

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal rate;

    @Column(name = "calculation_method", nullable = false, length = 50)
    private String calculationMethod; // Ej: FIXED_AMOUNT, HOURLY_RATE, PERCENTAGE

    @Column(name = "time_in_minutes")
    private Integer timeInMinutes;

    @Column(precision = 5, scale = 2)
    private BigDecimal percentage;

    @Column(name = "is_active")
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "master_fixed_cost_id")
    private UUID masterFixedCostId;

    @Column(name = "calculated_amount", nullable = false, precision = 15, scale = 4)
    private BigDecimal calculatedAmount;

    @Version
    @Builder.Default
    private Long version = 0L;
}
