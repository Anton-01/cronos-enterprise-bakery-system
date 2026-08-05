package com.ninsky.cronos.application.service.recipe;

import com.ninsky.cronos.application.request.recipe.RecipeFixedCostRequest;
import com.ninsky.cronos.application.request.recipe.RecipeIngredientRequest;
import com.ninsky.cronos.application.service.UnitConversionService;
import com.ninsky.cronos.domain.model.auth.User;
import com.ninsky.cronos.domain.model.core.MeasurementUnit;
import com.ninsky.cronos.domain.model.core.RawMaterial;
import com.ninsky.cronos.domain.model.recipe.*;
import com.ninsky.cronos.domain.port.core.MeasurementUnitRepositoryPort;
import com.ninsky.cronos.domain.port.core.RawMaterialRepositoryPort;
import com.ninsky.cronos.domain.port.recipe.IngredientSubstituteRepositoryPort;
import com.ninsky.cronos.domain.port.recipe.RecipeRepositoryPort;
import com.ninsky.cronos.domain.port.recipe.UserFixedCostRepositoryPort;
import com.ninsky.cronos.domain.service.core.RawMaterialCostingService;
import com.ninsky.cronos.infrastructure.exception.BusinessException;
import com.ninsky.cronos.infrastructure.exception.ResourceNotFoundException;
import com.ninsky.cronos.domain.port.auth.UserRepositoryPort;
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
public class RecipeDetailService {

    private final RecipeRepositoryPort recipeRepository;
    private final IngredientSubstituteRepositoryPort substituteRepository;
    private final RawMaterialRepositoryPort rawMaterialRepository;
    private final UserRepositoryPort userRepository;
    private final UserFixedCostRepositoryPort userFixedCostRepository;
    private final MeasurementUnitRepositoryPort unitRepository;

    private final UnitConversionService unitConversionService;
    private final RawMaterialCostingService costingService;

    @Transactional
    public void addIngredientToRecipe(String username, UUID recipeId, RecipeIngredientRequest request) {
        log.info("Added an ingredient to the recipe {} by user {}", recipeId, username);

        User user = userRepository.findByUsername(username).orElseThrow();
        Recipe recipe = recipeRepository.findByIdAndUserId(recipeId, user.getId()).orElseThrow(() -> new ResourceNotFoundException("Receta no encontrada o sin acceso"));

        RawMaterial material = rawMaterialRepository.findById(request.rawMaterialId()).orElseThrow();
        MeasurementUnit recipeUnit = unitRepository.findById(request.unitId()).orElseThrow(() -> new ResourceNotFoundException("Unidad de medida no encontrada"));
        MeasurementUnit purchaseUnit = unitRepository.findById(material.getPurchaseUnitId()).orElseThrow(() -> new ResourceNotFoundException("Unidad de compra no encontrada"));

        // UNIT CONVERSION CALCULATOR

        // Convert the amount specified in the recipe (e.g., 3 cups) to the unit used for the bag (e.g., kilograms)
        // The engine determines whether it is linear or requires cross-equivalence.
        BigDecimal quantityInPurchaseUnits = unitConversionService.convert(request.quantity(), recipeUnit, purchaseUnit, material.getId());

        // FINANCIAL CALCULATION WITH DEPRECIATION (Invoice price / purchase qty, adjusted for yield loss)
        BigDecimal realCostPerPurchasedUnit = costingService.calculatePurchaseUnitCost(
                material.getUnitCost(), material.getPurchaseQuantity(), material.getYieldPercentage());

        // Total cost for this line (e.g., 0.35 kg required * $18.3673 = $6.43)
        BigDecimal totalCost = quantityInPurchaseUnits.multiply(realCostPerPurchasedUnit).setScale(2, RoundingMode.HALF_UP);

        // Unit cost to be stored in the database (based on the unit selected by the user)
        // If 3 cups cost $6.43, then 1 cup costs $2.1433
        BigDecimal costPerRecipeUnit = totalCost.divide(request.quantity(), 6, RoundingMode.HALF_UP);

        int nextDisplayOrder = recipe.getIngredients().stream().mapToInt(ing -> ing.getDisplayOrder() != null ? ing.getDisplayOrder() : 0).max().orElse(0) + 1;

        RecipeIngredient ingredient = RecipeIngredient.builder().rawMaterialId(request.rawMaterialId()).quantity(request.quantity()).unitId(request.unitId())
                .displayOrder(request.displayOrder()).isOptional(request.isOptional()).notes(request.notes())
                .costPerUnit(costPerRecipeUnit).displayOrder(nextDisplayOrder).totalCost(totalCost).build();

        recipe.addIngredient(ingredient);
        recipe.setNeedsRecalculation(true);

        recipeRepository.save(recipe);
        log.info("Ingredient successfully added. Total cost of the line: ${}", totalCost);
    }

    @Transactional
    public void substituteAllergenIngredient(String username, UUID recipeId, UUID recipeIngredientId, UUID substituteMaterialId) {
        log.info("Starting ingredient substitution {} in recipe {}", recipeIngredientId, recipeId);

        User user = userRepository.findByUsername(username).orElseThrow();
        Recipe recipe = recipeRepository.findByIdAndUserId(recipeId, user.getId()).orElseThrow(() -> new ResourceNotFoundException("Receta no encontrada o sin acceso"));

        RecipeIngredient recipeIngredient = recipe.findIngredientById(recipeIngredientId).orElseThrow(() -> new ResourceNotFoundException("Ingrediente no encontrado en esta receta"));

        // Look up the conversion rule in the user's dictionary
        IngredientSubstitute rule = substituteRepository.findByUserIdAndOriginalIngredientIdAndSubstituteMaterialId(
                user.getId(), recipeIngredient.getRawMaterialId(), substituteMaterialId)
                .orElseThrow(() -> new BusinessException("No existe una regla de sustitución (Conversion Ratio) definida para estos ingredientes en tu catálogo."));

        // Use mathematics to scale the required amount
        BigDecimal oldQuantity = recipeIngredient.getQuantity();
        BigDecimal newQuantity = oldQuantity.multiply(rule.getConversionRatio()).setScale(4, RoundingMode.HALF_UP);

        // Determine the cost of the new replacement material
        RawMaterial newMaterial = rawMaterialRepository.findById(substituteMaterialId).orElseThrow();
        BigDecimal newCostPerUnit = newMaterial.getBaseUnitCost();
        BigDecimal newTotalCost = newQuantity.multiply(newCostPerUnit).setScale(2, RoundingMode.HALF_UP);

        // Maintain an audit trail (traceability) in the ingredient notes
        String auditNote = String.format("[SUSTITUCIÓN ALÉRGENO] Se reemplazó el original. Cantidad anterior: %s. Ratio de conversión aplicado: %s. Razón: %s. %s",
                oldQuantity, rule.getConversionRatio(), rule.getReason(),
                (recipeIngredient.getNotes() != null ? " Notas originales: " + recipeIngredient.getNotes() : ""));

        recipeIngredient.setRawMaterialId(substituteMaterialId);
        recipeIngredient.setQuantity(newQuantity);
        recipeIngredient.setCostPerUnit(newCostPerUnit);
        recipeIngredient.setTotalCost(newTotalCost);
        recipeIngredient.setNotes(auditNote);

        recipe.setNeedsRecalculation(true);

        // recipeIngredient is the same in-memory object living inside recipe.getIngredients() —
        // saving the aggregate root persists the mutated child via cascade, no separate save needed.
        recipeRepository.save(recipe);

        log.info("Replacement successful. New calculated value: {}", newQuantity);
    }

    @Transactional
    public void addFixedCostToRecipe(String username, UUID recipeId, RecipeFixedCostRequest request) {
        log.info("Linking the catalog's fixed cost to the recipe {} by {}", recipeId, username);

        User user = userRepository.findByUsername(username).orElseThrow();

        // Verify ownership of the recipe
        Recipe recipe = recipeRepository.findByIdAndUserId(recipeId, user.getId()).orElseThrow(() -> new ResourceNotFoundException("Receta no encontrada o sin acceso"));

        // Retrieve the master cost from the catalog (verifying that it belongs to this user)
        UserFixedCost masterCost = userFixedCostRepository.findByIdAndUserId(request.userFixedCostId(), user.getId())
                .orElseThrow(() -> new BusinessException("El costo fijo seleccionado no existe en tu catálogo."));

        BigDecimal appliedPercentage = request.percentage() != null ? request.percentage() : masterCost.getPercentage();

        if ("PERCENTAGE".equals(masterCost.getCalculationMethod())) {
            if (appliedPercentage == null || appliedPercentage.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("Debes especificar el porcentaje para este tipo de costo en el catálogo maestro o en la receta.");
            }
        }

        // Clone the data to the transactional entity (RecipeFixedCost)
        RecipeFixedCost recipeCost = RecipeFixedCost.builder().masterFixedCostId(masterCost.getId())
                .name(masterCost.getName()).description(masterCost.getDescription())
                .type(masterCost.getType()).rate(masterCost.getDefaultAmount())
                .calculationMethod(masterCost.getCalculationMethod()).timeInMinutes(request.timeInMinutes())
                .calculatedAmount(BigDecimal.ZERO).percentage(appliedPercentage).isActive(true).build();

        if ("HOURLY_RATE".equals(masterCost.getCalculationMethod()) && request.timeInMinutes() == null) {
            throw new BusinessException("Debes especificar el tiempo en minutos para los costos por hora.");
        }

        recipe.getFixedCosts().add(recipeCost);
        recipe.setNeedsRecalculation(true);

        recipeRepository.save(recipe);

        syncRecipeCosts(username, recipeId);
    }

    @Transactional
    public void removeIngredient(String username, UUID recipeId, UUID ingredientId) {
        log.info("User {} is removing the ingredient {} from the recipe {}", username, ingredientId, recipeId);

        User user = userRepository.findByUsername(username).orElseThrow();
        Recipe recipe = recipeRepository.findByIdAndUserId(recipeId, user.getId()).orElseThrow(() -> new ResourceNotFoundException("Receta no encontrada o sin acceso"));

        // Search for the ingredient WITHIN the recipe collection
        RecipeIngredient ingredientToRemove = recipe.getIngredients().stream().filter(ing -> ing.getId().equals(ingredientId))
                .findFirst().orElseThrow(() -> new ResourceNotFoundException("El ingrediente no pertenece a esta receta"));

        // Remove using the helper method (This triggers JPA's orphanRemoval)
        recipe.removeIngredient(ingredientToRemove);

        // Select this option for recalculation, as the total cost will decrease
        recipe.setNeedsRecalculation(true);
        recipeRepository.save(recipe);
        log.info("Ingredient removed and recipe marked for recalculation.");
    }

    @Transactional
    public void removeFixedCost(String username, UUID recipeId, UUID fixedCostId) {
        log.info("User {} removing the fixed cost {} from the recipe {}", username, fixedCostId, recipeId);

        User user = userRepository.findByUsername(username).orElseThrow();
        Recipe recipe = recipeRepository.findByIdAndUserId(recipeId, user.getId()).orElseThrow(() -> new ResourceNotFoundException("Receta no encontrada o sin acceso"));

        // Search for the ingredient WITHIN the recipe collection
        RecipeFixedCost fixedCostToRemove = recipe.getFixedCosts().stream()
                .filter(ing -> ing.getId().equals(fixedCostId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("El ingrediente no pertenece a esta receta"));

        // Remove using the helper method (This triggers JPA's orphanRemoval)
        recipe.removeFixedCost(fixedCostToRemove);
        recipe.setNeedsRecalculation(true);
        recipeRepository.save(recipe);
        log.info("Fixed cost removed and recipe marked for recalculation.");
    }

    @Transactional
    public void syncRecipeCosts(String username, UUID recipeId) {
        log.info("Synchronizing current catalog prices for recipe {} (User: {})", recipeId, username);

        User user = userRepository.findByUsername(username).orElseThrow();
        Recipe recipe = recipeRepository.findByIdAndUserId(recipeId, user.getId()).orElseThrow(() -> new ResourceNotFoundException("Receta no encontrada"));

        // If no recalculation is required, we don't use server resources
        if (!recipe.isNeedsRecalculation()) {
            return;
        }

        BigDecimal totalIngredientsCost = BigDecimal.ZERO;

        // update ingredients with current prices
        for (RecipeIngredient ingredient : recipe.getIngredients()) {
            // Here are the latest prices from the catalog
            RawMaterial material = rawMaterialRepository.findById(ingredient.getRawMaterialId()).orElseThrow();
            MeasurementUnit recipeUnit = unitRepository.findById(ingredient.getUnitId()).orElseThrow();
            MeasurementUnit purchaseUnit = unitRepository.findById(material.getPurchaseUnitId()).orElseThrow();

            // Convert Units
            BigDecimal quantityInPurchaseUnits = unitConversionService.convert(ingredient.getQuantity(), recipeUnit, purchaseUnit, material.getId());

            // Calculate the new actual cost per unit purchased
            BigDecimal realCostPerPurchasedUnit = costingService.calculatePurchaseUnitCost(
                    material.getUnitCost(), material.getPurchaseQuantity(), material.getYieldPercentage());

            // Calculate new totals
            BigDecimal newTotalCost = quantityInPurchaseUnits.multiply(realCostPerPurchasedUnit).setScale(2, RoundingMode.HALF_UP);
            BigDecimal newCostPerRecipeUnit = newTotalCost.divide(ingredient.getQuantity(), 6, RoundingMode.HALF_UP);

            // Update the “Photo” in the database
            ingredient.setCostPerUnit(newCostPerRecipeUnit);
            ingredient.setTotalCost(newTotalCost);

            totalIngredientsCost = totalIngredientsCost.add(newTotalCost);
        }

        // Add up the total times for the recipe
        int prepTime = recipe.getPreparationTimeMinutes() != null ? recipe.getPreparationTimeMinutes() : 0;
        int bakeTime = recipe.getBakingTimeMinutes() != null ? recipe.getBakingTimeMinutes() : 0;
        int totalRecipeTimeMinutes = prepTime + bakeTime;

        // Iterate over the fixed costs in this recipe
        BigDecimal fixedCostsSubtotal = BigDecimal.ZERO;
        for (RecipeFixedCost fixedCost : recipe.getFixedCosts()) {
            if (!fixedCost.isActive()) continue;

            // Manejo seguro de nulos inicial
            BigDecimal rate = fixedCost.getRate() != null ? fixedCost.getRate() : BigDecimal.ZERO;
            BigDecimal finalCalculatedAmount;

            switch (fixedCost.getCalculationMethod()) {

                case "FIXED_AMOUNT":
                case "PER_UNIT":
                case "FIXED_PER_BATCH":
                    finalCalculatedAmount = rate;
                    break;
                case "HOURLY_RATE":
                    // Dynamic logic: usamos el tiempo del costo o el de la receta
                    int minutesToApply = fixedCost.getTimeInMinutes() != null ? fixedCost.getTimeInMinutes() : totalRecipeTimeMinutes;
                    // (Hourly rate / 60) * Minutes
                    BigDecimal costPerMinute = rate.divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
                    finalCalculatedAmount = costPerMinute.multiply(BigDecimal.valueOf(minutesToApply));
                    break;

                case "PERCENTAGE":
                    BigDecimal percentage = fixedCost.getPercentage() != null ? fixedCost.getPercentage() : BigDecimal.ZERO;
                    // (Subtotal Ingredientes * Porcentaje) / 100
                    BigDecimal factor = percentage.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
                    finalCalculatedAmount = totalIngredientsCost.multiply(factor);
                    break;
                default:
                    finalCalculatedAmount = rate;
                    break;
            }

            finalCalculatedAmount = finalCalculatedAmount.setScale(2, RoundingMode.HALF_UP);
            fixedCost.setCalculatedAmount(finalCalculatedAmount);
            fixedCostsSubtotal = fixedCostsSubtotal.add(finalCalculatedAmount);
        }

        BigDecimal finalTotalCost = totalIngredientsCost.add(fixedCostsSubtotal).setScale(2, RoundingMode.HALF_UP);
        recipe.setTotalCost(finalTotalCost);
        recipe.setNeedsRecalculation(false);

        recipeRepository.save(recipe);
        log.info("Synchronization complete. The recipe ingredients now reflect current prices.");
    }
}
