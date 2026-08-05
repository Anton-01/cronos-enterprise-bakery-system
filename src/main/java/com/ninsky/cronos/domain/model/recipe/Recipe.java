package com.ninsky.cronos.domain.model.recipe;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Aggregate root. Embeds {@link RecipeIngredient}/{@link RecipeFixedCost}/{@link RecipeSubRecipeItem}
 * (true aggregate-internal children, no separate ports). Deliberately has <b>no {@code files} field</b>
 * — {@code RecipeFile} looks like a cascade-owned child (identical JPA shape to ingredients) but is
 * never actually persisted through Recipe's cascade in practice, only through its own independent
 * repository/service; it's a peer aggregate with its own port, referenced by id only, resolved via
 * {@code RecipeFileRepositoryPort.findByRecipeId(...)} wherever needed.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Recipe {
    private UUID id;
    private String name;
    private String description;
    private UUID categoryId;
    private BigDecimal yieldQuantity;
    private String yieldUnit;
    private Integer preparationTimeMinutes;
    private Integer bakingTimeMinutes;
    private Integer coolingTimeMinutes;
    @Builder.Default
    private String status = "DRAFT";
    @Builder.Default
    private boolean isActive = true;
    @Builder.Default
    private boolean needsRecalculation = false;
    @Builder.Default
    private Integer currentVersion = 1;
    private String instructions;
    private String storageInstructions;
    private Integer shelfLifeDays;
    private UUID userId;
    @Builder.Default
    private BigDecimal totalCost = BigDecimal.ZERO;
    @Builder.Default
    private Long version = 0L;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Builder.Default
    private List<RecipeIngredient> ingredients = new ArrayList<>();
    @Builder.Default
    private List<RecipeFixedCost> fixedCosts = new ArrayList<>();
    @Builder.Default
    private List<RecipeSubRecipeItem> subRecipes = new ArrayList<>();

    public void addIngredient(RecipeIngredient ingredient) {
        ingredients.add(ingredient);
    }

    public void removeIngredient(RecipeIngredient ingredient) {
        ingredients.remove(ingredient);
    }

    public void removeFixedCost(RecipeFixedCost fixedCost) {
        fixedCosts.remove(fixedCost);
    }

    public Optional<RecipeIngredient> findIngredientById(UUID ingredientId) {
        return ingredients.stream().filter(i -> i.getId().equals(ingredientId)).findFirst();
    }
}
