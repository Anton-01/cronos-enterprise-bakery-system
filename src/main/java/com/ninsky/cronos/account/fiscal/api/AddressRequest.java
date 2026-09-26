package com.ninsky.cronos.account.fiscal.api;

import com.ninsky.cronos.account.fiscal.api.validation.MexicanState;
import com.ninsky.cronos.account.fiscal.api.validation.MxZipCode;
import com.ninsky.cronos.account.shared.api.RejectUnknownProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;

/** Domicilio fiscal. Error {@code field}s are prefixed with {@code address.} (e.g. {@code address.zipCode}). */
@RejectUnknownProperties
@Schema(name = "AddressRequest")
public record AddressRequest(
        @Schema(example = "Av. Reforma", maxLength = 150)
        @NotBlank(message = "{account.validation.required}") @Size(max = 150, message = "{account.validation.size}")
        String street,

        @Schema(example = "222", maxLength = 20)
        @NotBlank(message = "{account.validation.required}") @Size(max = 20, message = "{account.validation.size}")
        String exteriorNumber,

        @Schema(nullable = true, maxLength = 20)
        @Size(max = 20, message = "{account.validation.size}")
        String interiorNumber,

        @Schema(example = "Juárez", maxLength = 100)
        @NotBlank(message = "{account.validation.required}") @Size(max = 100, message = "{account.validation.size}")
        String neighborhood,

        @Schema(example = "Cuauhtémoc", maxLength = 100)
        @NotBlank(message = "{account.validation.required}") @Size(max = 100, message = "{account.validation.size}")
        String municipality,

        @Schema(example = "CMX", description = "ISO 3166-2:MX code without the MX- prefix")
        @NotBlank(message = "{account.validation.required}") @MexicanState
        String state,

        @Schema(example = "06600", pattern = "^(0[1-9]|[1-9]\\d)\\d{3}$")
        @NotBlank(message = "{account.validation.required}") @MxZipCode
        String zipCode,

        @Schema(example = "MEX", allowableValues = "MEX")
        @NotBlank(message = "{account.validation.required}")
        @Pattern(regexp = "MEX", message = "{account.fiscal.address.country}")
        String country
) {
    public AddressRequest {
        street = trim(street);
        exteriorNumber = trim(exteriorNumber);
        interiorNumber = blankToNull(interiorNumber);
        neighborhood = trim(neighborhood);
        municipality = trim(municipality);
        state = state == null ? null : state.strip().toUpperCase(Locale.ROOT);
        zipCode = trim(zipCode);
        country = country == null ? null : country.strip().toUpperCase(Locale.ROOT);
    }

    private static String trim(String value) {
        return value == null ? null : value.strip();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
