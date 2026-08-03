package com.ninsky.cronos.presentation.controller.recipe;

import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.application.response.recipe.PublicSharedRecipeResponse;
import com.ninsky.cronos.application.service.recipe.RecipeShareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/public/recipes")
@RequiredArgsConstructor
@Tag(name = "Public Recipes", description = "Public access to shared recipes via ephemeral tokens")
public class PublicRecipeController {

    private final RecipeShareService shareService;

    @GetMapping("/share/{token}")
    @Operation(summary = "View shared recipe", description = "Retrieves a recipe. Tracks IP and User-Agent.")
    public ResponseEntity<ApiResponse<PublicSharedRecipeResponse>> viewSharedRecipe(@PathVariable String token, jakarta.servlet.http.HttpServletRequest request) {

        String ipAddress = request.getHeader("X-Forwarded-For");
        if (ipAddress == null || ipAddress.isEmpty()) {
            ipAddress = request.getRemoteAddr();
        }

        String userAgent = request.getHeader("User-Agent");
        PublicSharedRecipeResponse response = shareService.getSharedRecipe(token, ipAddress, userAgent);
        return ResponseEntity.ok(ApiResponse.success("Receta recuperada exitosamente", response));
    }
}
