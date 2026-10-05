package com.ninsky.cronos.presentation.controller.recipe;

import org.springframework.security.access.prepost.PreAuthorize;
import com.ninsky.cronos.iam.permission.Authorities;
import com.ninsky.cronos.application.request.recipe.RecipeFixedCostRequest;
import com.ninsky.cronos.application.request.recipe.RecipeIngredientRequest;
import com.ninsky.cronos.application.request.recipe.SubstituteIngredientRequest;
import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.application.service.recipe.RecipeDetailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@PreAuthorize(Authorities.RECIPE_UPDATE)
@RequestMapping("/recipes/{recipeId}")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Recipe Details", description = "Manage ingredients, sub-recipes, and fixed costs within a recipe")
public class RecipeDetailController {

    private final RecipeDetailService recipeDetailService;

    @PostMapping("/ingredients")
    @Operation(summary = "Add ingredient", description = "Adds a raw material to the recipe and calculates its line cost")
    public ResponseEntity<ApiResponse<Void>> addIngredient(@PathVariable UUID recipeId, Authentication authentication, @Valid @RequestBody RecipeIngredientRequest request) {
        recipeDetailService.addIngredientToRecipe(authentication.getName(), recipeId, request);
        return ResponseEntity.ok(ApiResponse.success("Ingrediente agregado exitosamente a la receta", null));
    }

    @PostMapping("/ingredients/{ingredientId}/substitute")
    @Operation(summary = "Substitute Allergen", description = "Replaces an ingredient with a substitute, calculating the new quantity based on the user's conversion ratio")
    public ResponseEntity<ApiResponse<Void>> substituteAllergen(@PathVariable UUID recipeId, @PathVariable UUID ingredientId, Authentication authentication, @Valid @RequestBody SubstituteIngredientRequest request) {
        recipeDetailService.substituteAllergenIngredient(authentication.getName(), recipeId, ingredientId, request.substituteMaterialId());
        return ResponseEntity.ok(ApiResponse.success("Ingrediente sustituido correctamente. Costos y cantidades recalculadas.", null));
    }

    @PostMapping("/fixed-costs")
    @Operation(summary = "Add Fixed Cost", description = "Links a fixed cost from the user's master catalog into this specific recipe")
    public ResponseEntity<ApiResponse<Void>> addFixedCost(@PathVariable UUID recipeId, Authentication authentication, @Valid @RequestBody RecipeFixedCostRequest request) {
        recipeDetailService.addFixedCostToRecipe(authentication.getName(), recipeId, request);
        return ResponseEntity.ok(ApiResponse.success("Costo fijo agregado a la receta exitosamente", null));
    }

    @DeleteMapping("/ingredients/{ingredientId}")
    @Operation(summary = "Remove ingredient", description = "Deletes an ingredient from the recipe and triggers a cost recalculation")
    public ResponseEntity<ApiResponse<Void>> removeIngredient(@PathVariable UUID recipeId, @PathVariable UUID ingredientId, Authentication authentication) {
        recipeDetailService.removeIngredient(authentication.getName(), recipeId, ingredientId);

        return ResponseEntity.ok(ApiResponse.success("Ingrediente eliminado exitosamente de la receta", null));
    }

    @DeleteMapping("/fixed-costs/{fixedCostId}")
    @Operation(summary = "Remove fixed cost", description = "Deletes a fixed cost from the recipe and triggers a cost recalculation")
    public ResponseEntity<ApiResponse<Void>> removeFixedCost(@PathVariable UUID recipeId, @PathVariable UUID fixedCostId, Authentication authentication) {
        recipeDetailService.removeFixedCost(authentication.getName(), recipeId, fixedCostId);

        return ResponseEntity.ok(ApiResponse.success("Costo fijo eliminado exitosamente de la receta", null));
    }

    @PostMapping("/sync-costs")
    @Operation(summary = "Sync latest costs", description = "Fetches the latest catalog prices and updates the recipe ingredients")
    public ResponseEntity<ApiResponse<Void>> syncRecipeCosts(@PathVariable UUID recipeId, Authentication authentication) {
        recipeDetailService.syncRecipeCosts(authentication.getName(), recipeId);
        return ResponseEntity.ok(ApiResponse.success("Costos de la receta actualizados con el catálogo", null));
    }
}
