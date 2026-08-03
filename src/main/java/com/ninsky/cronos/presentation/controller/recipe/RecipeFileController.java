package com.ninsky.cronos.presentation.controller.recipe;

import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.application.response.recipe.RecipeFileResponse;
import com.ninsky.cronos.application.service.recipe.RecipeFileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/recipes/{recipeId}/files")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Recipe Files", description = "Securely upload and manage attachments (images, PDFs) for a recipe")
public class RecipeFileController {

    private final RecipeFileService recipeFileService;

    @GetMapping
    @Operation(summary = "Get recipe files", description = "Retrieves all files attached to a specific recipe")
    public ResponseEntity<ApiResponse<List<RecipeFileResponse>>> getFiles(@PathVariable UUID recipeId, Authentication authentication) {
        List<RecipeFileResponse> files = recipeFileService.getFilesByRecipeId(authentication.getName(), recipeId);
        return ResponseEntity.ok(ApiResponse.success("Archivos recuperados exitosamente", files));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload recipe file", description = "Uploads a file and links it to the recipe. Protected against Path Traversal.")
    public ResponseEntity<ApiResponse<RecipeFileResponse>> uploadFile(@PathVariable UUID recipeId, @RequestPart("file") MultipartFile file, @RequestParam(value = "description", required = false) String description, Authentication authentication) {
        RecipeFileResponse response = recipeFileService.uploadRecipeFile(authentication.getName(), recipeId, file, description);
        return ResponseEntity.ok(ApiResponse.success("Archivo subido y vinculado a la receta exitosamente", response));
    }

    @DeleteMapping("/{fileId}")
    @Operation(summary = "Delete recipe file", description = "Permanently deletes a file from the recipe and storage")
    public ResponseEntity<ApiResponse<Void>> deleteFile(@PathVariable UUID recipeId, @PathVariable UUID fileId, Authentication authentication) {
        recipeFileService.deleteRecipeFile(authentication.getName(), recipeId, fileId);
        return ResponseEntity.ok(ApiResponse.success("Archivo eliminado exitosamente", null));
    }
}
