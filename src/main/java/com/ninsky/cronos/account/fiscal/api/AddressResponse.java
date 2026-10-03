package com.ninsky.cronos.account.fiscal.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ninsky.cronos.account.fiscal.domain.MexicanState;
import io.swagger.v3.oas.annotations.media.Schema;

/** {@code interiorNumber} is serialized as {@code null} when absent — the frontend relies on the key. */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "AddressResponse")
public record AddressResponse(
        String street,
        String exteriorNumber,
        String interiorNumber,
        String neighborhood,
        String municipality,
        @Schema(type = "string", example = "CMX") MexicanState state,
        String zipCode,
        String country
) {
}
