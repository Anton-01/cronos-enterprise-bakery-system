package com.ninsky.cronos.application.service.recipe;

import com.ninsky.cronos.application.response.recipe.RecipeCostBreakdown;
import com.ninsky.cronos.domain.model.core.RawMaterial;
import com.ninsky.cronos.domain.entity.recipes.Recipe;
import com.ninsky.cronos.domain.entity.recipes.RecipeFixedCost;
import com.ninsky.cronos.domain.entity.recipes.RecipeIngredient;
import com.ninsky.cronos.domain.entity.recipes.RecipeSubRecipe;
import com.ninsky.cronos.domain.port.core.RawMaterialRepositoryPort;
import com.ninsky.cronos.infrastructure.exception.BusinessException;
import com.ninsky.cronos.infrastructure.exception.ResourceNotFoundException;
import com.ninsky.cronos.infrastructure.persistence.recipe.RecipeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecipeCalculationService {

    private final RecipeRepository recipeRepository;
    private final RawMaterialRepositoryPort rawMaterialRepository;

    private static final int SCALE = 6;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    /**
     * Calculate the cost of a recipe, optionally scaling it to a new quantity.
     * @param recipeId Parent recipe ID.
     * @param targetYield Desired performance (If null, calculate using the original performance).
     * @return The complete financial breakdown.
     */
    @Transactional
    public RecipeCostBreakdown calculateRecipeCost(UUID recipeId, BigDecimal targetYield) {
        log.info("Starting cost calculation for recipe ID: {} with target yield: {}", recipeId, targetYield);

        Recipe recipe = recipeRepository.findById(recipeId).orElseThrow(() -> new ResourceNotFoundException("Receta no encontrada"));

        return calculateRecipeCostInternal(recipe, targetYield);
    }

    private RecipeCostBreakdown calculateRecipeCostInternal(Recipe recipe, BigDecimal targetYield) {
        BigDecimal originalYield = recipe.getYieldQuantity();

        // 1. Determinar el Yield objetivo
        BigDecimal actualTargetYield = (targetYield != null && targetYield.compareTo(BigDecimal.ZERO) > 0)
                ? targetYield
                : originalYield;

        // 2. Factor de Escala (Protección: asumimos que originalYield nunca es 0 en BD)
        BigDecimal scaleFactor = actualTargetYield.divide(originalYield, 6, RoundingMode.HALF_UP);

        // 3. Cálculos delegados escalados
        BigDecimal materialsCost = calculateMaterialsCost(recipe, scaleFactor);
        BigDecimal subRecipesCost = calculateSubRecipesCost(recipe, scaleFactor);
        BigDecimal fixedCosts = calculateFixedCosts(recipe, scaleFactor); // ¡Este ahora usará la versión optimizada!

        // 4. Totales finales
        BigDecimal totalCost = materialsCost.add(subRecipesCost).add(fixedCosts).setScale(2, RoundingMode.HALF_UP);
        BigDecimal costPerUnit = totalCost.divide(actualTargetYield, 6, RoundingMode.HALF_UP);

        return RecipeCostBreakdown.builder().targetYield(actualTargetYield).yieldUnit(recipe.getYieldUnit()).scaleFactor(scaleFactor)
                .materialsCost(materialsCost).subRecipesCost(subRecipesCost)
                .fixedCosts(fixedCosts).totalCost(totalCost)
                .costPerUnit(costPerUnit).build();
    }

    private BigDecimal calculateMaterialsCost(Recipe recipe, BigDecimal scaleFactor) {
        BigDecimal total = BigDecimal.ZERO;

        for (RecipeIngredient ingredient : recipe.getIngredients()) {
            BigDecimal currentCostPerUnit = ingredient.getCostPerUnit();
            if (currentCostPerUnit == null) {
                RawMaterial material = rawMaterialRepository.findById(ingredient.getRawMaterialId()).orElseThrow(() -> new ResourceNotFoundException("Materia prima no encontrada en catálogo"));

                currentCostPerUnit = material.getUnitCost();

                if (currentCostPerUnit == null) {
                    throw new BusinessException("La materia prima '" + material.getName() + "' no tiene un costo definido. Por favor, actualiza tu inventario.");
                }

                log.warn("Using a cost fallback for ingredient {} in recipe {}", ingredient.getRawMaterialId(), recipe.getId());
            }

            BigDecimal scaledQuantity = ingredient.getQuantity().multiply(scaleFactor);
            BigDecimal ingredientCost = scaledQuantity.multiply(currentCostPerUnit);

            total = total.add(ingredientCost);
        }
        return total.setScale(SCALE, ROUNDING_MODE);
    }

    private BigDecimal calculateSubRecipesCost(Recipe recipe, BigDecimal scaleFactor) {
        BigDecimal total = BigDecimal.ZERO;

        for (RecipeSubRecipe subRecipeRel : recipe.getSubRecipes()) {
            Recipe childRecipe = subRecipeRel.getSubRecipe();

            BigDecimal scaledRequiredQuantity = subRecipeRel.getQuantity().multiply(scaleFactor);
            RecipeCostBreakdown childCost = calculateRecipeCostInternal(childRecipe, scaledRequiredQuantity);
            total = total.add(childCost.totalCost());
        }
        return total.setScale(SCALE, ROUNDING_MODE);
    }

    private BigDecimal calculateFixedCosts(Recipe recipe, BigDecimal scaleFactor) {
        BigDecimal total = BigDecimal.ZERO;

        for (RecipeFixedCost fixedCost : recipe.getFixedCosts()) {
            if (!fixedCost.isActive()) continue;

            BigDecimal baseCost = fixedCost.getCalculatedAmount() != null ? fixedCost.getCalculatedAmount() : BigDecimal.ZERO;

            BigDecimal costAmount = switch (fixedCost.getCalculationMethod()) {
                case "FIXED_PER_BATCH" -> baseCost; // El costo no escala (ej. renta de la cocina)
                case "PER_UNIT", "HOURLY_RATE", "PERCENTAGE" -> baseCost.multiply(scaleFactor); // Escalan directamente proporcional al volumen
                default -> baseCost.multiply(scaleFactor);
            };

            total = total.add(costAmount);
        }

        return total.setScale(SCALE, ROUNDING_MODE);
    }
}
