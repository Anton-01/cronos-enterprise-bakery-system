package com.ninsky.cronos.kitchen.ingredient;

import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.infrastructure.web.StrictApiContract;
import com.ninsky.cronos.infrastructure.web.paging.CatalogPage;
import com.ninsky.cronos.kitchen.shared.KitchenAccess;
import com.ninsky.cronos.kitchen.shared.KitchenMessages;
import com.ninsky.cronos.kitchen.shared.KitchenStatus;
import com.ninsky.cronos.kitchen.shared.Scope;
import com.ninsky.cronos.kitchen.shared.StatusRequest;
import com.ninsky.cronos.kitchen.shared.Warned;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.UUID;

@RestController
@StrictApiContract
@RequiredArgsConstructor
@RequestMapping("/ingredients")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Kitchen · Ingredients", description = "SYSTEM ∪ own ingredients, prices, ripple, substitutes (§4)")
public class IngredientController {

    private final IngredientService service;
    private final KitchenMessages messages;

    @GetMapping
    @PreAuthorize(KitchenAccess.INGREDIENT_READ)
    @Operation(summary = "Search ingredients", description = "Sort whitelist: name, costPerBaseUnit, pricedAt, usedInRecipes. "
            + "allergenIds + excludeAllergens=false → contains any; true → free of all.")
    public ResponseEntity<ApiResponse<CatalogPage<IngredientSummary>>> page(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort, @RequestParam(required = false) String search,
            @RequestParam(name = "categoryIds", required = false) List<Long> categoryIds,
            @RequestParam(name = "allergenIds", required = false) List<Long> allergenIds,
            @RequestParam(defaultValue = "false") boolean excludeAllergens, @RequestParam(required = false) Scope scope,
            @RequestParam(required = false) Boolean priceStale, @RequestParam(required = false) KitchenStatus status) {
        IngredientFilter filter = new IngredientFilter(search, categoryIds, allergenIds, excludeAllergens, scope, priceStale, status);
        return ResponseEntity.ok(ApiResponse.success(null, service.page(filter, page, size, sort)));
    }

    @GetMapping("/stats")
    @PreAuthorize(KitchenAccess.INGREDIENT_READ)
    @Operation(summary = "Header counters over visible ACTIVE ingredients")
    public ResponseEntity<ApiResponse<IngredientStats>> stats() {
        return ResponseEntity.ok(ApiResponse.success(null, service.stats()));
    }

    @GetMapping("/{id}")
    @PreAuthorize(KitchenAccess.INGREDIENT_READ)
    @Operation(summary = "Ingredient detail", description = "Includes both prices, substitutes and detection suggestions.")
    public ResponseEntity<ApiResponse<IngredientDetail>> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(null, service.detail(id)));
    }

    @PostMapping
    @PreAuthorize(KitchenAccess.INGREDIENT_CREATE)
    @Operation(summary = "Create a USER ingredient", description = "Optional first price.")
    public ResponseEntity<ApiResponse<IngredientDetail>> create(@RequestBody IngredientRequest request) {
        IngredientDetail created = service.create(request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequestUri().path("/{id}").buildAndExpand(created.summary().id()).toUri())
                .body(ApiResponse.success(messages.get("kitchen.ingredient.created", created.summary().name()), created));
    }

    @PutMapping("/{id}")
    @PreAuthorize(KitchenAccess.INGREDIENT_EDIT)
    @Operation(summary = "Update an ingredient", description = "USER rows need INGREDIENT.UPDATE; SYSTEM rows CATALOG.INGREDIENT.MANAGE.")
    public ResponseEntity<ApiResponse<IngredientDetail>> update(@PathVariable UUID id, @RequestBody IngredientRequest request) {
        IngredientDetail updated = service.update(id, request);
        return ResponseEntity.ok(ApiResponse.success(messages.get("kitchen.ingredient.updated", updated.summary().name()), updated));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize(KitchenAccess.INGREDIENT_EDIT)
    @Operation(summary = "Activate or deactivate", description = "Deactivating flags recipes using it as STALE.")
    public ResponseEntity<ApiResponse<IngredientDetail>> changeStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest request) {
        IngredientDetail updated = service.changeStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success(messages.get("kitchen.ingredient.statusChanged", updated.summary().name()), updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(KitchenAccess.INGREDIENT_DELETE)
    @Operation(summary = "Delete an unused USER ingredient")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success(messages.get("kitchen.ingredient.deleted"), null));
    }

    @PostMapping("/{id}/prices")
    @PreAuthorize(KitchenAccess.INGREDIENT_UPDATE)
    @Operation(summary = "Register an own price", description = "Recalculates the caller's recipes, flags open quotes, reports margins.")
    public ResponseEntity<ApiResponse<PriceImpact>> registerPrice(@PathVariable UUID id, @RequestBody IngredientPriceRequest request) {
        Warned<PriceImpact> impact = service.registerPrice(id, request);
        return ResponseEntity.ok(ApiResponse.success(messages.get("kitchen.ingredient.priceRegistered", impact.data().recipesAffected()),
                impact.data(), impact.warnings()));
    }

    @GetMapping("/{id}/prices")
    @PreAuthorize(KitchenAccess.INGREDIENT_READ)
    @Operation(summary = "Price history", description = "Own prices and reference changes, newest first.")
    public ResponseEntity<ApiResponse<CatalogPage<PriceHistoryEntry>>> prices(@PathVariable UUID id,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(ApiResponse.success(null, service.prices(id, page, size)));
    }

    @GetMapping("/{id}/substitutes")
    @PreAuthorize(KitchenAccess.INGREDIENT_READ)
    @Operation(summary = "Declared substitutes", description = "Optionally only those free of the given allergens.")
    public ResponseEntity<ApiResponse<List<SubstituteResponse>>> substitutes(@PathVariable UUID id,
            @RequestParam(name = "freeOfAllergenIds", required = false) List<Long> freeOfAllergenIds) {
        return ResponseEntity.ok(ApiResponse.success(null, service.substitutes(id, freeOfAllergenIds)));
    }

    @GetMapping("/{id}/usage")
    @PreAuthorize(KitchenAccess.INGREDIENT_READ)
    @Operation(summary = "Caller recipes using it")
    public ResponseEntity<ApiResponse<List<IngredientUsage>>> usage(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(null, service.usage(id)));
    }
}
