package com.ninsky.cronos.infrastructure.persistence.recipe.entity;

import com.ninsky.cronos.domain.entity.base.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "recipe_ingredients")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecipeIngredientJpaEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id", nullable = false)
    private RecipeJpaEntity recipe;

    @Column(name = "raw_material_id", nullable = false)
    private UUID rawMaterialId;

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal quantity;

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(name = "is_optional")
    @Builder.Default
    private boolean isOptional = false;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "cost_per_unit", precision = 15, scale = 6)
    private BigDecimal costPerUnit;

    @Column(name = "total_cost", precision = 15, scale = 2)
    private BigDecimal totalCost;

    @Version
    @Builder.Default
    private Long version = 0L;
}
