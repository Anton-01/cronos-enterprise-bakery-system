package com.ninsky.cronos.kitchen.allergen;

import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.infrastructure.web.StrictApiContract;
import com.ninsky.cronos.kitchen.shared.KitchenAccess;
import com.ninsky.cronos.kitchen.shared.KitchenMessages;
import com.ninsky.cronos.kitchen.shared.KitchenStatus;
import com.ninsky.cronos.kitchen.shared.StatusRequest;
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

@RestController("kitchenAllergenController")
@StrictApiContract
@RequiredArgsConstructor
@RequestMapping("/allergens")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Kitchen · Allergens", description = "SYSTEM ∪ tenant allergens, keywords and detection (§3)")
public class AllergenController {

    private final AllergenService service;
    private final KitchenMessages messages;

    @GetMapping
    @PreAuthorize(KitchenAccess.ALLERGEN_READ)
    @Operation(summary = "List allergens", description = "Unpaged, sorted by localised name.")
    public ResponseEntity<ApiResponse<List<AllergenResponse>>> list(@RequestParam(required = false) KitchenStatus status) {
        return ResponseEntity.ok(ApiResponse.success(null, service.list(status)));
    }

    @GetMapping("/{id}")
    @PreAuthorize(KitchenAccess.ALLERGEN_READ)
    @Operation(summary = "Get an allergen")
    public ResponseEntity<ApiResponse<AllergenResponse>> get(@PathVariable long id) {
        return ResponseEntity.ok(ApiResponse.success(null, service.get(id)));
    }

    @PostMapping
    @PreAuthorize(KitchenAccess.ALLERGEN_MANAGE)
    @Operation(summary = "Create a USER allergen")
    public ResponseEntity<ApiResponse<AllergenResponse>> create(@RequestBody AllergenRequest request) {
        AllergenResponse created = service.create(request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequestUri().path("/{id}").buildAndExpand(created.id()).toUri())
                .body(ApiResponse.success(messages.get("kitchen.allergen.created", created.name()), created));
    }

    @PutMapping("/{id}")
    @PreAuthorize(KitchenAccess.ALLERGEN_MANAGE)
    @Operation(summary = "Update an allergen", description = "SYSTEM rows accept only extra tenant keywords (409 SYSTEM_RESOURCE_CONFLICT otherwise).")
    public ResponseEntity<ApiResponse<AllergenResponse>> update(@PathVariable long id, @RequestBody AllergenRequest request) {
        AllergenResponse updated = service.update(id, request);
        return ResponseEntity.ok(ApiResponse.success(messages.get("kitchen.allergen.updated", updated.name()), updated));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize(KitchenAccess.ALLERGEN_MANAGE)
    @Operation(summary = "Activate or deactivate a USER allergen")
    public ResponseEntity<ApiResponse<AllergenResponse>> changeStatus(@PathVariable long id, @Valid @RequestBody StatusRequest request) {
        AllergenResponse updated = service.changeStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success(messages.get("kitchen.allergen.statusChanged", updated.name()), updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(KitchenAccess.ALLERGEN_MANAGE)
    @Operation(summary = "Delete an unused USER allergen")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success(messages.get("kitchen.allergen.deleted"), null));
    }

    @PostMapping("/detect")
    @PreAuthorize(KitchenAccess.ALLERGEN_READ)
    @Operation(summary = "Detect allergens in free text", description = "Whole-word keyword match in both locales; suggestions only.")
    public ResponseEntity<ApiResponse<List<DetectedAllergen>>> detect(@Valid @RequestBody DetectRequest request) {
        return ResponseEntity.ok(ApiResponse.success(null, service.detect(request)));
    }
}
