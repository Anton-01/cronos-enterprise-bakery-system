package com.ninsky.cronos.kitchen.legacy;

import com.ninsky.cronos.application.request.recipe.RecipeFixedCostRequest;
import com.ninsky.cronos.application.request.recipe.RecipeIngredientRequest;
import com.ninsky.cronos.application.request.recipe.SubstituteIngredientRequest;
import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.kitchen.recipe.CostPreview;
import com.ninsky.cronos.kitchen.recipe.RecipeService;
import com.ninsky.cronos.kitchen.shared.KitchenAccess;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

/** Deprecated per-line recipe endpoints (§13); every write goes through {@code PUT /recipes/{id}}. */
@Deprecated(forRemoval = true)
@RestController
@RequiredArgsConstructor
@RequestMapping("/recipes/{recipeId}")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Kitchen · Recipes (deprecated)", description = "Use PUT /recipes/{id} and POST /recipes/cost-preview")
public class LegacyRecipeController {

    private final LegacyRecipeEditor editor;
    private final RecipeService recipes;

    @PostMapping("/ingredients")
    @PreAuthorize(KitchenAccess.RECIPE_UPDATE)
    @Operation(summary = "Add a line (deprecated)", deprecated = true)
    public ResponseEntity<ApiResponse<Void>> addIngredient(@PathVariable UUID recipeId, @Valid @RequestBody RecipeIngredientRequest request) {
        editor.addLine(recipeId, request);
        return ResponseEntity.ok(ApiResponse.success(null, null));
    }

    @DeleteMapping("/ingredients/{lineId}")
    @PreAuthorize(KitchenAccess.RECIPE_UPDATE)
    @Operation(summary = "Remove a line (deprecated)", deprecated = true)
    public ResponseEntity<ApiResponse<Void>> removeIngredient(@PathVariable UUID recipeId, @PathVariable UUID lineId) {
        editor.removeLine(recipeId, lineId);
        return ResponseEntity.ok(ApiResponse.success(null, null));
    }

    @PostMapping("/ingredients/{lineId}/substitute")
    @PreAuthorize(KitchenAccess.RECIPE_UPDATE)
    @Operation(summary = "Swap a line's ingredient (deprecated)", deprecated = true)
    public ResponseEntity<ApiResponse<Void>> substitute(@PathVariable UUID recipeId, @PathVariable UUID lineId,
                                                       @Valid @RequestBody SubstituteIngredientRequest request) {
        editor.substitute(recipeId, lineId, request.substituteMaterialId());
        return ResponseEntity.ok(ApiResponse.success(null, null));
    }

    @PostMapping("/fixed-costs")
    @PreAuthorize(KitchenAccess.RECIPE_UPDATE)
    @Operation(summary = "Add a fixed cost (deprecated)", deprecated = true)
    public ResponseEntity<ApiResponse<Void>> addFixedCost(@PathVariable UUID recipeId, @Valid @RequestBody RecipeFixedCostRequest request) {
        editor.addFixedCost(recipeId, request);
        return ResponseEntity.ok(ApiResponse.success(null, null));
    }

    @DeleteMapping("/fixed-costs/{fixedCostId}")
    @PreAuthorize(KitchenAccess.RECIPE_UPDATE)
    @Operation(summary = "Remove a fixed cost (deprecated)", deprecated = true)
    public ResponseEntity<ApiResponse<Void>> removeFixedCost(@PathVariable UUID recipeId, @PathVariable UUID fixedCostId) {
        editor.removeFixedCost(recipeId, fixedCostId);
        return ResponseEntity.ok(ApiResponse.success(null, null));
    }

    @GetMapping("/cost")
    @PreAuthorize(KitchenAccess.RECIPE_READ)
    @Operation(summary = "Cost breakdown (deprecated)", description = "Now the cost-preview shape", deprecated = true)
    public ResponseEntity<ApiResponse<CostPreview>> cost(@PathVariable UUID recipeId, @RequestParam(required = false) BigDecimal targetYield) {
        return ResponseEntity.ok(ApiResponse.success(null, editor.cost(recipeId, targetYield)));
    }

    @PostMapping("/sync-costs")
    @PreAuthorize(KitchenAccess.RECIPE_UPDATE)
    @Operation(summary = "Recalculate (deprecated)", deprecated = true)
    public ResponseEntity<ApiResponse<Void>> syncCosts(@PathVariable UUID recipeId) {
        recipes.recalculate(recipeId);
        return ResponseEntity.ok(ApiResponse.success(null, null));
    }
}
