package com.ninsky.cronos.presentation.controller.recipe;

import com.ninsky.cronos.application.request.recipe.UserFixedCostRequest;
import com.ninsky.cronos.application.request.recipe.UserFixedCostStatusRequest;
import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.application.response.recipe.UserFixedCostResponse;
import com.ninsky.cronos.application.service.recipe.UserFixedCostService;
import com.ninsky.cronos.iam.permission.Authorities;
import com.ninsky.cronos.kitchen.shared.KitchenMessages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
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

import java.util.UUID;

@RestController
@PreAuthorize(Authorities.FIXED_COST_READ)
@RequestMapping("/user-fixed-cost")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "User Fixed Costs Catalog", description = "The user's master catalog of fixed costs (labour, utilities, packaging…), baking-studio §4")
public class UserFixedCostController {

    private final UserFixedCostService fixedCostService;
    private final KitchenMessages messages;

    @PostMapping
    @PreAuthorize(Authorities.FIXED_COST_MANAGE)
    @Operation(summary = "Create fixed cost", description = "Adds a fixed cost; up to 4 decimals for the amount; monthlyAmount/monthlyBasis go together")
    public ResponseEntity<ApiResponse<UserFixedCostResponse>> createFixedCost(@Valid @RequestBody UserFixedCostRequest request) {
        UserFixedCostResponse response = fixedCostService.createFixedCost(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Costo fijo creado exitosamente", response));
    }

    @GetMapping
    @Operation(summary = "Get my fixed costs", description = "Paginated, active and inactive rows. The first call seeds the default catalog.")
    public ResponseEntity<ApiResponse<Page<UserFixedCostResponse>>> getMyFixedCosts(Pageable pageable, @RequestParam(required = false) String search) {
        Page<UserFixedCostResponse> costs = fixedCostService.getMyFixedCosts(pageable, search);
        return ResponseEntity.ok(ApiResponse.success("Catálogo recuperado exitosamente", costs));
    }

    @PutMapping("/{id}")
    @PreAuthorize(Authorities.FIXED_COST_MANAGE)
    @Operation(summary = "Update fixed cost", description = "Recipes using it become STALE when the amount, percentage or method changes")
    public ResponseEntity<ApiResponse<UserFixedCostResponse>> updateFixedCost(@PathVariable UUID id, @Valid @RequestBody UserFixedCostRequest request) {
        UserFixedCostResponse response = fixedCostService.updateFixedCost(id, request);
        return ResponseEntity.ok(ApiResponse.success("Costo fijo actualizado exitosamente", response));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize(Authorities.FIXED_COST_MANAGE)
    @Operation(summary = "Activate or deactivate", description = "Recipes that already use an inactive cost keep it; it cannot be added to new rows")
    public ResponseEntity<ApiResponse<UserFixedCostResponse>> setStatus(@PathVariable UUID id, @Valid @RequestBody UserFixedCostStatusRequest request) {
        UserFixedCostResponse response = fixedCostService.setActive(id, request.isActive());
        return ResponseEntity.ok(ApiResponse.success(messages.get(request.isActive() ? "kitchen.fixedCost.activated" : "kitchen.fixedCost.deactivated"),
                response));
    }

    @PostMapping("/restore-defaults")
    @PreAuthorize(Authorities.FIXED_COST_MANAGE)
    @Operation(summary = "Restore default fixed costs", description = "Re-creates the defaults the user deleted; never renames or removes rows")
    public ResponseEntity<ApiResponse<Void>> restoreDefaults() {
        int restored = fixedCostService.restoreDefaults();
        return ResponseEntity.ok(ApiResponse.success(messages.get("kitchen.fixedCost.defaultsRestored", restored), null));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Authorities.FIXED_COST_MANAGE)
    @Operation(summary = "Delete fixed cost", description = "409 RESOURCE_IN_USE (errors[0].details.recipes) while a recipe uses it; deactivate it instead")
    public ResponseEntity<ApiResponse<Void>> deleteFixedCost(@PathVariable UUID id) {
        fixedCostService.deleteFixedCost(id);
        return ResponseEntity.ok(ApiResponse.success("Costo fijo eliminado del catálogo", null));
    }
}
