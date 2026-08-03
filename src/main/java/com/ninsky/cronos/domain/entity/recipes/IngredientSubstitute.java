package com.ninsky.cronos.domain.entity.recipes;


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
public class IngredientSubstitute extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    // Aislamiento SaaS: A quién le pertenece esta regla de sustitución
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "original_ingredient_id", nullable = false)
    private UUID originalIngredientId;

    @Column(name = "substitute_material_id", nullable = false)
    private UUID substituteMaterialId;

    // Por defecto es 1.0 (Ej: 1kg de Azúcar Blanca = 1kg de Azúcar Morena)
    @Column(name = "conversion_ratio", nullable = false, precision = 10, scale = 4)
    @Builder.Default
    private BigDecimal conversionRatio = BigDecimal.ONE;

    @Column(length = 50)
    private String reason; // Ej: "Alérgeno: Gluten", "Preferencia: Vegano"

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Version
    @Builder.Default
    private Long version = 0L;
}
