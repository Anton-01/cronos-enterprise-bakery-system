package com.ninsky.cronos.account.fiscal.api;

import com.ninsky.cronos.account.fiscal.api.validation.NoCorporateSuffix;
import com.ninsky.cronos.account.fiscal.api.validation.RegimeMatchesRfc;
import com.ninsky.cronos.account.fiscal.api.validation.SatTaxRegime;
import com.ninsky.cronos.account.fiscal.api.validation.ValidRfc;
import com.ninsky.cronos.account.fiscal.domain.LegalName;
import com.ninsky.cronos.account.fiscal.domain.Rfc;
import com.ninsky.cronos.account.shared.api.OpenApiExamples;
import com.ninsky.cronos.account.shared.api.RejectUnknownProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * PUT /users/me/fiscal body. No {@code taxpayerType} (derived from the RFC) and no {@code updatedAt}:
 * sending either is a 400, like any other unknown property.
 */
@RejectUnknownProperties
@RegimeMatchesRfc
@Schema(name = "UpsertFiscalDataRequest", example = OpenApiExamples.FISCAL_REQUEST)
public record UpsertFiscalDataRequest(
        @Schema(example = "PASTELERIA CRONOS", maxLength = 254, description = "Upper-cased server-side; no corporate regime suffix")
        @NotBlank(message = "{account.fiscal.legalName.required}")
        @Size(max = LegalName.MAX_LENGTH, message = "{account.validation.size}")
        @NoCorporateSuffix
        String legalName,

        @Schema(example = "GODE561231GR8", description = "RFC: 13 chars (persona física) or 12 (persona moral)")
        @NotBlank(message = "{account.fiscal.taxId.required}")
        @ValidRfc
        String taxId,

        @Schema(example = "626", description = "SAT c_RegimenFiscal key")
        @NotBlank(message = "{account.fiscal.taxRegime.required}")
        @SatTaxRegime
        String taxRegime,

        @NotNull(message = "{account.validation.required}")
        @Valid
        AddressRequest address
) {
    public UpsertFiscalDataRequest {
        legalName = LegalName.normalize(legalName);
        taxId = Rfc.normalize(taxId);
        taxRegime = taxRegime == null ? null : taxRegime.strip();
    }
}
