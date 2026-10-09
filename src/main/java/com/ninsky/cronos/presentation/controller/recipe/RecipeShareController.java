package com.ninsky.cronos.presentation.controller.recipe;

import org.springframework.security.access.prepost.PreAuthorize;
import com.ninsky.cronos.iam.permission.Authorities;
import com.ninsky.cronos.application.request.recipe.CreateRecipeShareRequest;
import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.application.response.recipe.RecipeShareAccessLogResponse;
import com.ninsky.cronos.application.response.recipe.RecipeShareResponse;
import com.ninsky.cronos.application.service.recipe.RecipeShareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@PreAuthorize(Authorities.RECIPE_SHARE)
@RequestMapping("/recipes/{recipeId}/shares")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Recipe Sharing", description = "Generate and manage ephemeral share links for a recipe")
public class RecipeShareController {

    private final RecipeShareService shareService;

    @GetMapping
    @Operation(summary = "List recipe shares", description = "The caller's links for an own or SYSTEM library recipe; 404 when the recipe is not visible")
    public ResponseEntity<ApiResponse<List<RecipeShareResponse>>> getShares(@PathVariable UUID recipeId) {
        List<RecipeShareResponse> shares = shareService.getSharesByRecipeId(recipeId);
        return ResponseEntity.ok(ApiResponse.success("Enlaces recuperados exitosamente", shares));
    }

    @PostMapping
    @Operation(summary = "Generate share link", description = "Creates a time-limited public link to view the recipe")
    public ResponseEntity<ApiResponse<RecipeShareResponse>> generateLink(@PathVariable UUID recipeId, @Valid @RequestBody CreateRecipeShareRequest request) {
        RecipeShareResponse response = shareService.generateShareLink(recipeId, request);
        return ResponseEntity.ok(ApiResponse.success("Enlace efímero generado con éxito", response));
    }

    @DeleteMapping("/{shareId}/revoke")
    @Operation(summary = "Revoke share link", description = "Instantly invalidates a previously generated share link")
    public ResponseEntity<ApiResponse<Void>> revokeLink(@PathVariable UUID recipeId, @PathVariable UUID shareId) {
        shareService.revokeShareLink(recipeId, shareId);
        return ResponseEntity.ok(ApiResponse.success("Enlace revocado permanentemente", null));
    }

    @GetMapping("/{shareId}/analytics")
    @Operation(summary = "Get access logs", description = "Returns the detailed history of who/when opened this link")
    public ResponseEntity<ApiResponse<List<RecipeShareAccessLogResponse>>> getAnalytics(@PathVariable UUID recipeId, @PathVariable UUID shareId) {
        List<RecipeShareAccessLogResponse> analytics = shareService.getShareAnalytics(recipeId, shareId);
        return ResponseEntity.ok(ApiResponse.success("Analíticas recuperadas exitosamente", analytics));
    }
}