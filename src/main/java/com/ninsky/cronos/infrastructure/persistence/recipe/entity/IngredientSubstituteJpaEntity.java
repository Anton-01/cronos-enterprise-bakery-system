package com.ninsky.cronos.infrastructure.persistence.recipe.entity;

import com.ninsky.cronos.domain.entity.base.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "ingredient_substitutes", indexes = {
        @Index(name = "idx_user_ingredient_subs", columnList = "user_id, original_ingredient_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IngredientSubstituteJpaEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "original_ingredient_id", nullable = false)
    private UUID originalIngredientId;

    @Column(name = "substitute_material_id", nullable = false)
    private UUID substituteMaterialId;

    @Column(name = "conversion_ratio", nullable = false, precision = 10, scale = 4)
    @Builder.Default
    private BigDecimal conversionRatio = BigDecimal.ONE;

    @Column(length = 50)
    private String reason;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Version
    @Builder.Default
    private Long version = 0L;
}
