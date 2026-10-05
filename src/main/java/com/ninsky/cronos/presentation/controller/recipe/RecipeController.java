package com.ninsky.cronos.presentation.controller.recipe;

import org.springframework.security.access.prepost.PreAuthorize;
import com.ninsky.cronos.iam.permission.Authorities;
import com.ninsky.cronos.application.request.recipe.CreateRecipeRequest;
import com.ninsky.cronos.application.request.recipe.UpdateRecipeRequest;
import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.application.response.recipe.RecipeCostBreakdown;
import com.ninsky.cronos.application.response.recipe.RecipeDetailResponse;
import com.ninsky.cronos.application.response.recipe.RecipeResponse;
import com.ninsky.cronos.application.response.recipe.SimpleRecipeResponse;
import com.ninsky.cronos.application.service.recipe.RecipeCalculationService;
import com.ninsky.cronos.application.service.recipe.RecipeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@PreAuthorize(Authorities.RECIPE_READ)
@RequestMapping("/recipes")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Recipe Management", description = "Endpoints for recipe calculation, creation, and management")
public class RecipeController {

    private final RecipeCalculationService recipeCalculationService;
    private final RecipeService recipeService;

    @GetMapping("/{id}")
    @Operation(summary = "Get recipe details", description = "Retrieves the full details of a recipe, including its ingredients and fixed costs.")
    public ResponseEntity<ApiResponse<RecipeDetailResponse>> getRecipeById(@PathVariable UUID id, Authentication authentication) {
        RecipeDetailResponse response = recipeService.getRecipeById(authentication.getName(), id);
        return ResponseEntity.ok(ApiResponse.success("Detalles de la receta recuperados exitosamente", response));
    }

    @GetMapping("/simple")
    @Operation(summary = "Search simple recipes", description = "Returns a lightweight list of recipes optimized for dropdowns and typeaheads")
    public ResponseEntity<ApiResponse<List<SimpleRecipeResponse>>> searchSimpleRecipes(Authentication authentication, @RequestParam(required = false, defaultValue = "") String search) {
        List<SimpleRecipeResponse> responses = recipeService.searchSimpleRecipes(authentication.getName(), search);
        return ResponseEntity.ok(ApiResponse.success("Simple recipes retrieved successfully", responses));
    }

    // ==========================================
    // CÁLCULO Y COSTEO (El Motor Financiero)
    // ==========================================

    @GetMapping("/{id}/cost")
    @Operation(summary = "Calculate Recipe Cost", description = "Calculates the total cost of a recipe. You can provide a 'targetYield' to scale the recipe dynamically.")
    public ResponseEntity<ApiResponse<RecipeCostBreakdown>> calculateRecipeCost(@PathVariable UUID id, @RequestParam(required = false) BigDecimal targetYield) {
        log.info("REST request to calculate cost for Recipe ID: {}, Target Yield: {}", id, targetYield);
        RecipeCostBreakdown breakdown = recipeCalculationService.calculateRecipeCost(id, targetYield);
        String message = targetYield != null ? "Recipe scaled and calculated successfully" : "Base recipe cost calculated successfully";
        return ResponseEntity.ok(ApiResponse.success(message, breakdown));
    }

    @PostMapping
    @PreAuthorize(Authorities.RECIPE_CREATE)
    @Operation(summary = "Create a new recipe", description = "Creates a new draft recipe linked to the authenticated user")
    public ResponseEntity<ApiResponse<RecipeResponse>> createRecipe(Authentication authentication, @Valid @RequestBody CreateRecipeRequest request) {
        RecipeResponse response = recipeService.createRecipe(authentication.getName(), request);
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(ApiResponse.success("Receta creada exitosamente", response));
    }

    @GetMapping
    @Operation(summary = "Get my recipes", description = "Returns a paginated list of recipes belonging to the current user")
    public ResponseEntity<ApiResponse<Page<RecipeResponse>>> getMyRecipes(Authentication authentication, org.springframework.data.domain.Pageable pageable, @RequestParam(required = false) String search) {
        Page<RecipeResponse> recipes = recipeService.getMyRecipes(authentication.getName(), pageable, search);
        return ResponseEntity.ok(ApiResponse.success("Recetas recuperadas exitosamente", recipes));
    }

    @PutMapping("/{id}")
    @PreAuthorize(Authorities.RECIPE_UPDATE)
    @Operation(summary = "Update recipe", description = "Updates an existing recipe. Validates ownership automatically.")
    public ResponseEntity<ApiResponse<RecipeResponse>> updateRecipe(@PathVariable UUID id, Authentication authentication, @Valid @RequestBody UpdateRecipeRequest request) {
        RecipeResponse response = recipeService.updateRecipe(authentication.getName(), id, request);
        return ResponseEntity.ok(ApiResponse.success("Receta actualizada exitosamente", response));
    }
}
