package com.ninsky.cronos.presentation.controller.recipe;

import org.springframework.security.access.prepost.PreAuthorize;
import com.ninsky.cronos.iam.permission.Authorities;
import com.ninsky.cronos.application.request.recipe.UserFixedCostRequest;
import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.application.response.recipe.UserFixedCostResponse;
import com.ninsky.cronos.application.service.recipe.UserFixedCostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@PreAuthorize(Authorities.FIXED_COST_READ)
@RequestMapping("/user-fixed-cost")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "User Fixed Costs Catalog", description = "CRUD endpoints for the user's master catalog of fixed costs (Labor, Utilities, etc.)")
public class UserFixedCostController {

    private final UserFixedCostService fixedCostService;

    @PostMapping
    @PreAuthorize(Authorities.FIXED_COST_MANAGE)
    @Operation(summary = "Create fixed cost", description = "Adds a new fixed cost to the user's master catalog")
    public ResponseEntity<ApiResponse<UserFixedCostResponse>> createFixedCost(Authentication authentication, @Valid @RequestBody UserFixedCostRequest request) {
        UserFixedCostResponse response = fixedCostService.createFixedCost(authentication.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Costo fijo creado exitosamente", response));
    }

    @GetMapping
    @Operation(summary = "Get my fixed costs", description = "Returns a paginated list of active fixed costs from the user's catalog")
    public ResponseEntity<ApiResponse<Page<UserFixedCostResponse>>> getMyFixedCosts(Authentication authentication, Pageable pageable, @RequestParam(required = false) String search) {
        Page<UserFixedCostResponse> costs = fixedCostService.getMyFixedCosts(authentication.getName(), pageable, search);
        return ResponseEntity.ok(ApiResponse.success("Catálogo recuperado exitosamente", costs));
    }

    @PutMapping("/{id}")
    @PreAuthorize(Authorities.FIXED_COST_MANAGE)
    @Operation(summary = "Update fixed cost", description = "Updates the details and default amount of an existing fixed cost")
    public ResponseEntity<ApiResponse<UserFixedCostResponse>> updateFixedCost(@PathVariable UUID id, Authentication authentication, @Valid @RequestBody UserFixedCostRequest request) {
        UserFixedCostResponse response = fixedCostService.updateFixedCost(authentication.getName(), id, request);
        return ResponseEntity.ok(ApiResponse.success("Costo fijo actualizado exitosamente", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Authorities.FIXED_COST_MANAGE)
    @Operation(summary = "Delete fixed cost", description = "Soft deletes a fixed cost so it no longer appears in new recipes")
    public ResponseEntity<ApiResponse<Void>> deleteFixedCost(@PathVariable UUID id, Authentication authentication) {
        fixedCostService.deleteFixedCost(authentication.getName(), id);
        return ResponseEntity.ok(ApiResponse.success("Costo fijo eliminado del catálogo", null));
    }
}
