package com.ninsky.cronos.finance.taxrate;

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
@RequestMapping("/finance/tax-rates")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Finance · IVA rates", description = "IVA rate catalog aligned with SAT CFDI 4.0 and tenant default (spec §10)")
public class TaxRateController {

    private final TaxRateService service;
    private final FinanceMessages messages;

    @GetMapping
    @PreAuthorize(FinanceAccess.TAX_RATE_READ)
    @Operation(summary = "Search IVA rates", description = "Sort whitelist: name, ratePercent, validFrom, status (default ratePercent,desc).")
    public ResponseEntity<ApiResponse<CatalogPage<TaxRateResponse>>> page(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort, @RequestParam(required = false) String search,
            @RequestParam(required = false) FinanceStatus status) {
        return ResponseEntity.ok(ApiResponse.success(null, service.page(search, status, page, size, sort)));
    }

    @GetMapping("/catalog")
    @PreAuthorize(FinanceAccess.AUTHENTICATED)
    @Operation(summary = "Selectable IVA rates", description = "ACTIVE and valid today (America/Mexico_City), default first; unpaged.")
    public ResponseEntity<ApiResponse<List<TaxRateOption>>> catalog() {
        return ResponseEntity.ok(ApiResponse.success(null, service.catalog()));
    }

    @PostMapping
    @PreAuthorize(FinanceAccess.TAX_RATE_MANAGE)
    @Operation(summary = "Create an IVA rate", description = "satTaxCode is always 002; EXENTO requires ratePercent = null.")
    public ResponseEntity<ApiResponse<TaxRateResponse>> create(@Valid @RequestBody TaxRateRequest request) {
        TaxRateResponse created = service.create(request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequestUri().path("/{id}").buildAndExpand(created.id()).toUri())
                .body(ApiResponse.success(messages.get("finance.taxRate.created", created.code()), created));
    }

    @PutMapping("/{id}")
    @PreAuthorize(FinanceAccess.TAX_RATE_MANAGE)
    @Operation(summary = "Update an IVA rate", description = "While in use only name, description and validTo may change.")
    public ResponseEntity<ApiResponse<TaxRateResponse>> update(@PathVariable long id, @Valid @RequestBody TaxRateRequest request) {
        TaxRateResponse updated = service.update(id, request);
        return ResponseEntity.ok(ApiResponse.success(messages.get("finance.taxRate.updated", updated.code()), updated));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize(FinanceAccess.TAX_RATE_MANAGE)
    @Operation(summary = "Activate or deactivate", description = "The default rate cannot be deactivated (DEFAULT_LOCKED).")
    public ResponseEntity<ApiResponse<TaxRateResponse>> changeStatus(@PathVariable long id, @Valid @RequestBody StatusRequest request) {
        TaxRateResponse updated = service.changeStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success(messages.get("finance.taxRate.statusChanged", updated.code()), updated));
    }

    @PatchMapping("/{id}/default")
    @PreAuthorize(FinanceAccess.SETTINGS_UPDATE)
    @Operation(summary = "Make the tenant default", description = "Target must be ACTIVE and currently valid.")
    public ResponseEntity<ApiResponse<TaxRateResponse>> makeDefault(@PathVariable long id, @Valid @RequestBody VersionRequest request) {
        TaxRateResponse updated = service.makeDefault(id, request);
        return ResponseEntity.ok(ApiResponse.success(messages.get("finance.taxRate.defaultChanged", updated.code()), updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(FinanceAccess.TAX_RATE_MANAGE)
    @Operation(summary = "Delete an unused, non-default IVA rate")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success(messages.get("finance.taxRate.deleted"), null));
    }
}
