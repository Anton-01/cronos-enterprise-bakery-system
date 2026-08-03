package com.ninsky.cronos.domain.entity.recipes;

import com.ninsky.cronos.domain.entity.auth.User;
import com.ninsky.cronos.domain.entity.base.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "recipes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Recipe extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "yield_quantity", nullable = false, precision = 10, scale = 2)
    private BigDecimal yieldQuantity;

    @Column(name = "yield_unit", nullable = false, length = 50)
    private String yieldUnit;

    @Column(name = "preparation_time_minutes")
    private Integer preparationTimeMinutes;

    @Column(name = "baking_time_minutes")
    private Integer bakingTimeMinutes;

    @Column(name = "cooling_time_minutes")
    private Integer coolingTimeMinutes;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "DRAFT";

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "needs_recalculation", nullable = false)
    @Builder.Default
    private boolean needsRecalculation = false;

    @Column(name = "current_version", nullable = false)
    @Builder.Default
    private Integer currentVersion = 1;

    @Column(length = 5000)
    private String instructions;

    @Column(name = "storage_instructions", length = 1000)
    private String storageInstructions;

    @Column(name = "shelf_life_days")
    private Integer shelfLifeDays;

    @Version
    @Builder.Default
    private Long version = 0L;

    // ==========================================
    // RELACIONES (El poder del Aggregate Root)
    // ==========================================

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<RecipeIngredient> ingredients = new ArrayList<>();

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<RecipeFixedCost> fixedCosts = new ArrayList<>();

    @OneToMany(mappedBy = "parentRecipe", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<RecipeSubRecipe> subRecipes = new ArrayList<>();

    // Métodos Helper para mantener la sincronización bidireccional
    public void addIngredient(RecipeIngredient ingredient) {
        ingredients.add(ingredient);
        ingredient.setRecipe(this);
    }

    public void removeIngredient(RecipeIngredient ingredient) {
        ingredients.remove(ingredient);
        ingredient.setRecipe(null);
    }

    public void removeFixedCost(RecipeFixedCost fixedCost) {
        fixedCosts.remove(fixedCost);
        fixedCost.setRecipe(null);
    }

    // ==========================================
    // RELACIONES: ARCHIVOS (FOTOS Y DOCUMENTOS)
    // ==========================================

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<RecipeFile> files = new ArrayList<>();

    // Métodos Helper para mantener la relación bidireccional sincronizada en memoria
    public void addFile(RecipeFile file) {
        this.files.add(file);
        file.setRecipe(this);
    }

    public void removeFile(RecipeFile file) {
        this.files.remove(file);
        file.setRecipe(null);
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // ==========================================
    // DATOS FINANCIEROS (Caché de Rendimiento)
    // ==========================================

    @Column(name = "total_cost", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalCost = BigDecimal.ZERO;
}