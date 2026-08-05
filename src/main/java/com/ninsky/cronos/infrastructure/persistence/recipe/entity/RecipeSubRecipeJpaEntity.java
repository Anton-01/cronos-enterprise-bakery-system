package com.ninsky.cronos.infrastructure.persistence.recipe.entity;

import com.ninsky.cronos.domain.entity.base.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Self-referential: both FKs point at {@link RecipeJpaEntity}. Kept as real JPA {@code @ManyToOne}
 * associations (pure infra plumbing) even though the domain model ({@code RecipeSubRecipeItem})
 * only exposes {@code subRecipeId} — {@code RecipeMapper} reads {@code getSubRecipe().getId()} off
 * the lazy proxy, which resolves from the FK column alone without triggering a load.
 */
@Entity
@Table(name = "recipe_sub_recipes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecipeSubRecipeJpaEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_recipe_id", nullable = false)
    private RecipeJpaEntity parentRecipe;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_recipe_id", nullable = false)
    private RecipeJpaEntity subRecipe;

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal quantity;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "total_cost", precision = 15, scale = 2)
    private BigDecimal totalCost;

    @Version
    @Builder.Default
    private Long version = 0L;
}
