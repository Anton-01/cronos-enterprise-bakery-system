package com.ninsky.cronos.kitchen.recipe;

import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.infrastructure.web.StrictApiContract;
import com.ninsky.cronos.infrastructure.web.paging.CatalogPage;
import com.ninsky.cronos.kitchen.costing.CostStatus;
import com.ninsky.cronos.kitchen.recipe.file.RecipeFileResponse;
import com.ninsky.cronos.kitchen.recipe.file.RecipeFileService;
import com.ninsky.cronos.kitchen.recipe.file.RecipeFileUpdate;
import com.ninsky.cronos.kitchen.shared.KitchenAccess;
import com.ninsky.cronos.kitchen.shared.KitchenMessages;
import com.ninsky.cronos.kitchen.shared.Scope;
import com.ninsky.cronos.kitchen.shared.Warned;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.UUID;

@RestController
@StrictApiContract
@RequiredArgsConstructor
@RequestMapping("/recipes")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Kitchen · Recipes", description = "Recipe aggregate, cost preview, revisions and files (§5)")
public class RecipeController {

    private final RecipeService service;
    private final CostPreviewService previews;
    private final RecipeFileService files;
    private final KitchenMessages messages;

    @GetMapping
    @PreAuthorize(KitchenAccess.RECIPE_READ)
    @Operation(summary = "Search recipes", description = "Sort whitelist: updatedAt, name, costPerUnit. freeOfAllergenIds checks contained allergens only.")
    public ResponseEntity<ApiResponse<CatalogPage<RecipeSummary>>> page(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort, @RequestParam(required = false) String search,
            @RequestParam(name = "categoryIds", required = false) List<Long> categoryIds,
            @RequestParam(name = "statuses", required = false) List<RecipeStatus> statuses,
            @RequestParam(name = "freeOfAllergenIds", required = false) List<Long> freeOfAllergenIds,
            @RequestParam(required = false) CostStatus costStatus, @RequestParam(required = false) Scope scope) {
        RecipeFilter filter = new RecipeFilter(search, categoryIds, statuses, freeOfAllergenIds, costStatus, scope);
        return ResponseEntity.ok(ApiResponse.success(null, service.page(filter, page, size, sort)));
    }

    @GetMapping("/stats")
    @PreAuthorize(KitchenAccess.RECIPE_READ)
    @Operation(summary = "Header counters of the caller's recipes")
    public ResponseEntity<ApiResponse<RecipeStats>> stats() {
        return ResponseEntity.ok(ApiResponse.success(null, service.stats()));
    }

    @GetMapping("/simple")
    @PreAuthorize(KitchenAccess.RECIPE_READ)
    @Operation(summary = "ACTIVE recipes for the quote picker")
    public ResponseEntity<ApiResponse<List<RecipeOption>>> simple(@RequestParam(required = false) String search) {
        return ResponseEntity.ok(ApiResponse.success(null, service.simple(search)));
    }

    @PostMapping("/cost-preview")
    @PreAuthorize(KitchenAccess.RECIPE_READ)
    @Operation(summary = "Price a draft or a configuration", description = "Never persists; 120 requests/min per user.")
    public ResponseEntity<ApiResponse<CostPreview>> costPreview(@RequestBody CostPreviewRequest request) {
        return ResponseEntity.ok(ApiResponse.success(null, previews.preview(request)));
    }

    @GetMapping("/{id}")
    @PreAuthorize(KitchenAccess.RECIPE_READ)
    @Operation(summary = "Recipe detail")
    public ResponseEntity<ApiResponse<RecipeDetail>> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(null, service.detail(id)));
    }

    @PostMapping
    @PreAuthorize(KitchenAccess.RECIPE_CREATE)
    @Operation(summary = "Create a recipe (DRAFT)")
    public ResponseEntity<ApiResponse<RecipeDetail>> create(@RequestBody RecipeRequest request) {
        RecipeDetail created = service.create(request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequestUri().path("/{id}").buildAndExpand(created.summary().id()).toUri())
                .body(ApiResponse.success(messages.get("kitchen.recipe.created", created.summary().name()), created));
    }

    @PutMapping("/{id}")
    @PreAuthorize(KitchenAccess.RECIPE_UPDATE)
    @Operation(summary = "Replace the aggregate", description = "Lines are matched by id; omitted lines are deleted.")
    public ResponseEntity<ApiResponse<RecipeDetail>> update(@PathVariable UUID id, @RequestBody RecipeRequest request) {
        RecipeDetail updated = service.update(id, request);
        return ResponseEntity.ok(ApiResponse.success(messages.get("kitchen.recipe.updated", updated.summary().name()), updated));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize(KitchenAccess.RECIPE_UPDATE)
    @Operation(summary = "Change status", description = "Publishing reports unpriced lines and an empty process as warnings.")
    public ResponseEntity<ApiResponse<RecipeDetail>> changeStatus(@PathVariable UUID id, @Valid @RequestBody RecipeStatusRequest request) {
        Warned<RecipeDetail> updated = service.changeStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success(messages.get("kitchen.recipe.statusChanged", updated.data().summary().name()),
                updated.data(), updated.warnings()));
    }

    @PostMapping("/{id}/duplicate")
    @PreAuthorize(KitchenAccess.RECIPE_CREATE)
    @Operation(summary = "Duplicate (also SYSTEM library recipes)")
    public ResponseEntity<ApiResponse<RecipeDetail>> duplicate(@PathVariable UUID id, @RequestBody DuplicateRequest request) {
        RecipeDetail copy = service.duplicate(id, request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentContextPath().path("/recipes/{id}").buildAndExpand(copy.summary().id()).toUri())
                .body(ApiResponse.success(messages.get("kitchen.recipe.duplicated", copy.summary().name()), copy));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(KitchenAccess.RECIPE_DELETE)
    @Operation(summary = "Soft delete", description = "Quotes keep their snapshot; files are purged later.")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success(messages.get("kitchen.recipe.deleted"), null));
    }

    @PostMapping("/{id}/recalculate")
    @PreAuthorize(KitchenAccess.RECIPE_UPDATE)
    @Operation(summary = "Recalculate with today's prices")
    public ResponseEntity<ApiResponse<RecipeDetail>> recalculate(@PathVariable UUID id) {
        RecipeDetail detail = service.recalculate(id);
        return ResponseEntity.ok(ApiResponse.success(messages.get("kitchen.recipe.recalculated"), detail));
    }

    @GetMapping("/{id}/history")
    @PreAuthorize(KitchenAccess.RECIPE_READ)
    @Operation(summary = "Revision history, newest first")
    public ResponseEntity<ApiResponse<CatalogPage<RecipeRevision>>> history(@PathVariable UUID id,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(ApiResponse.success(null, service.history(id, page, size)));
    }

    @PostMapping(value = "/{id}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(KitchenAccess.RECIPE_UPDATE)
    @Operation(summary = "Attach one file", description = "Verified by content; 25 MB, 40 files per recipe, 500 MB per tenant.")
    public ResponseEntity<ApiResponse<RecipeFileResponse>> upload(@PathVariable UUID id, @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String description) {
        RecipeFileResponse created = files.upload(id, file, description);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequestUri().path("/{fileId}").buildAndExpand(created.id()).toUri())
                .body(ApiResponse.success(messages.get("kitchen.file.uploaded", created.fileName()), created));
    }

    @PatchMapping("/{id}/files/{fileId}")
    @PreAuthorize(KitchenAccess.RECIPE_UPDATE)
    @Operation(summary = "Edit description or cover")
    public ResponseEntity<ApiResponse<RecipeFileResponse>> updateFile(@PathVariable UUID id, @PathVariable UUID fileId,
            @RequestBody RecipeFileUpdate request) {
        return ResponseEntity.ok(ApiResponse.success(messages.get("kitchen.file.updated"), files.update(id, fileId, request)));
    }

    @DeleteMapping("/{id}/files/{fileId}")
    @PreAuthorize(KitchenAccess.RECIPE_UPDATE)
    @Operation(summary = "Delete a file")
    public ResponseEntity<ApiResponse<Void>> deleteFile(@PathVariable UUID id, @PathVariable UUID fileId) {
        files.delete(id, fileId);
        return ResponseEntity.ok(ApiResponse.success(messages.get("kitchen.file.deleted"), null));
    }
}
