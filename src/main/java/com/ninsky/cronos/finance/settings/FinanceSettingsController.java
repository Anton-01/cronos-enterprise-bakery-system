package com.ninsky.cronos.finance.settings;

import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.finance.shared.FinanceAccess;
import com.ninsky.cronos.finance.shared.FinanceMessages;
import com.ninsky.cronos.infrastructure.web.StrictApiContract;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@StrictApiContract
@RequiredArgsConstructor
@RequestMapping("/finance/settings")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Finance · Settings", description = "Tenant defaults and calculation rules (spec §11)")
public class FinanceSettingsController {

    private final FinanceSettingsService service;
    private final FinanceMessages messages;

    @GetMapping
    @PreAuthorize(FinanceAccess.AUTHENTICATED)
    @Operation(summary = "Defaults and calculation rules", description = "Read by every module that prices documents.")
    public ResponseEntity<ApiResponse<FinanceSettingsResponse>> get() {
        return ResponseEntity.ok(ApiResponse.success(null, service.current()));
    }

    @PutMapping
    @PreAuthorize(FinanceAccess.SETTINGS_UPDATE)
    @Operation(summary = "Update calculation rules", description = "pricesIncludeTax and roundingMode; defaults change via PATCH …/default.")
    public ResponseEntity<ApiResponse<FinanceSettingsResponse>> update(@Valid @RequestBody FinanceSettingsRequest request) {
        return ResponseEntity.ok(ApiResponse.success(messages.get("finance.settings.updated"), service.update(request)));
    }
}
