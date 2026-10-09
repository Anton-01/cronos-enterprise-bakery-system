package com.ninsky.cronos.kitchen.section;

import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.infrastructure.web.StrictApiContract;
import com.ninsky.cronos.kitchen.shared.KitchenAccess;
import com.ninsky.cronos.kitchen.shared.KitchenMessages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.UUID;

@RestController
@StrictApiContract
@RequiredArgsConstructor
@RequestMapping("/recipe-sections")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Kitchen · Recipe sections", description = "The user's own ingredient-group labels (baking-studio §3)")
public class RecipeSectionController {

    private final RecipeSectionService service;
    private final KitchenMessages messages;

    @GetMapping
    @PreAuthorize(KitchenAccess.RECIPE_READ)
    @Operation(summary = "List my section labels", description = "Ordered by displayOrder, name; the first call seeds the defaults.")
    public ResponseEntity<ApiResponse<List<RecipeSection>>> list() {
        return ResponseEntity.ok(ApiResponse.success(null, service.list()));
    }

    @PostMapping
    @PreAuthorize(KitchenAccess.RECIPE_UPDATE)
    @Operation(summary = "Create a label", description = "Appended at the end; 409 DUPLICATE_RESOURCE under accent/case variants, QUOTA_EXCEEDED past 60.")
    public ResponseEntity<ApiResponse<RecipeSection>> create(@RequestBody RecipeSectionRequest request) {
        RecipeSection created = service.create(request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequestUri().path("/{id}").buildAndExpand(created.id()).toUri())
                .body(ApiResponse.success(messages.get("kitchen.section.created", created.name()), created));
    }

    @PutMapping("/order")
    @PreAuthorize(KitchenAccess.RECIPE_UPDATE)
    @Operation(summary = "Reorder labels", description = "Listed ids take 0..n-1; unknown ids → 400; the rest keep their relative order after them.")
    public ResponseEntity<ApiResponse<List<RecipeSection>>> reorder(@RequestBody RecipeSectionOrderRequest request) {
        return ResponseEntity.ok(ApiResponse.success(null, service.reorder(request)));
    }

    @PostMapping("/restore-defaults")
    @PreAuthorize(KitchenAccess.RECIPE_UPDATE)
    @Operation(summary = "Restore default labels", description = "Re-creates missing defaults; never renames or deletes.")
    public ResponseEntity<ApiResponse<List<RecipeSection>>> restoreDefaults() {
        return ResponseEntity.ok(ApiResponse.success(messages.get("kitchen.section.defaultsRestored"), service.restoreDefaults()));
    }

    @PutMapping("/{id}")
    @PreAuthorize(KitchenAccess.RECIPE_UPDATE)
    @Operation(summary = "Rename or recolour a label", description = "Recipe lines are never rewritten.")
    public ResponseEntity<ApiResponse<RecipeSection>> update(@PathVariable UUID id, @RequestBody RecipeSectionRequest request) {
        RecipeSection updated = service.update(id, request);
        return ResponseEntity.ok(ApiResponse.success(messages.get("kitchen.section.updated", updated.name()), updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(KitchenAccess.RECIPE_UPDATE)
    @Operation(summary = "Delete a label", description = "Allowed while lines use it; they keep their text.")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success(messages.get("kitchen.section.deleted"), null));
    }
}
