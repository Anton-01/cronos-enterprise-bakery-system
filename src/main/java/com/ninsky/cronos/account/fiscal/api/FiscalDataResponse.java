package com.ninsky.cronos.account.fiscal.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ninsky.cronos.account.fiscal.domain.TaxRegime;
import com.ninsky.cronos.account.fiscal.domain.TaxpayerType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "FiscalDataResponse")
public record FiscalDataResponse(
        String legalName,
        String taxId,
        @Schema(type = "string", example = "626") TaxRegime taxRegime,
        @Schema(description = "Derived from the RFC length") TaxpayerType taxpayerType,
        AddressResponse address,
        LocalDateTime updatedAt
) {
}
