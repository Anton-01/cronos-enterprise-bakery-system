package com.ninsky.cronos.finance.currency;

import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.finance.shared.FinanceAccess;
import com.ninsky.cronos.finance.shared.FinanceMessages;
import com.ninsky.cronos.finance.shared.FinanceStatus;
import com.ninsky.cronos.finance.shared.StatusRequest;
import com.ninsky.cronos.finance.shared.VersionRequest;
import com.ninsky.cronos.infrastructure.web.StrictApiContract;
import com.ninsky.cronos.infrastructure.web.paging.CatalogPage;
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

@RestController
@StrictApiContract
@RequiredArgsConstructor
@RequestMapping("/finance/currencies")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Finance · Currencies", description = "ISO 4217 currency catalog and tenant default (spec §9)")
public class CurrencyController {

    private final CurrencyService service;
    private final FinanceMessages messages;

    @GetMapping
    @PreAuthorize(FinanceAccess.CURRENCY_READ)
    @Operation(summary = "Search currencies", description = "Sort whitelist: code, name, status (default code,asc).")
    public ResponseEntity<ApiResponse<CatalogPage<CurrencyResponse>>> page(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort, @RequestParam(required = false) String search,
            @RequestParam(required = false) FinanceStatus status) {
        return ResponseEntity.ok(ApiResponse.success(null, service.page(search, status, page, size, sort)));
    }

    @GetMapping("/catalog")
    @PreAuthorize(FinanceAccess.AUTHENTICATED)
    @Operation(summary = "Active currencies for pickers", description = "Default first, then by code; unpaged.")
    public ResponseEntity<ApiResponse<List<CurrencyOption>>> catalog() {
        return ResponseEntity.ok(ApiResponse.success(null, service.catalog()));
    }

    @PostMapping
    @PreAuthorize(FinanceAccess.CURRENCY_MANAGE)
    @Operation(summary = "Create a currency")
    public ResponseEntity<ApiResponse<CurrencyResponse>> create(@Valid @RequestBody CurrencyRequest request) {
        CurrencyResponse created = service.create(request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequestUri().path("/{id}").buildAndExpand(created.id()).toUri())
                .body(ApiResponse.success(messages.get("finance.currency.created", created.code()), created));
    }

    @PutMapping("/{id}")
    @PreAuthorize(FinanceAccess.CURRENCY_MANAGE)
    @Operation(summary = "Update a currency", description = "code, numericCode and decimalPlaces are frozen while the currency is in use.")
    public ResponseEntity<ApiResponse<CurrencyResponse>> update(@PathVariable long id, @Valid @RequestBody CurrencyRequest request) {
        CurrencyResponse updated = service.update(id, request);
        return ResponseEntity.ok(ApiResponse.success(messages.get("finance.currency.updated", updated.code()), updated));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize(FinanceAccess.CURRENCY_MANAGE)
    @Operation(summary = "Activate or deactivate", description = "The default currency cannot be deactivated (DEFAULT_LOCKED).")
    public ResponseEntity<ApiResponse<CurrencyResponse>> changeStatus(@PathVariable long id, @Valid @RequestBody StatusRequest request) {
        CurrencyResponse updated = service.changeStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success(messages.get("finance.currency.statusChanged", updated.code()), updated));
    }

    @PatchMapping("/{id}/default")
    @PreAuthorize(FinanceAccess.SETTINGS_UPDATE)
    @Operation(summary = "Make the tenant default", description = "Target must be ACTIVE; the previous default is unset atomically.")
    public ResponseEntity<ApiResponse<CurrencyResponse>> makeDefault(@PathVariable long id, @Valid @RequestBody VersionRequest request) {
        CurrencyResponse updated = service.makeDefault(id, request);
        return ResponseEntity.ok(ApiResponse.success(messages.get("finance.currency.defaultChanged", updated.code()), updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(FinanceAccess.CURRENCY_MANAGE)
    @Operation(summary = "Delete an unused, non-default currency")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success(messages.get("finance.currency.deleted"), null));
    }
}
