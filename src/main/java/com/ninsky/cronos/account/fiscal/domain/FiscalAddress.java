package com.ninsky.cronos.account.fiscal.domain;

import com.ninsky.cronos.account.shared.domain.DomainValidationException;

import java.util.Objects;

/** Domicilio fiscal. Mexico only: {@code country} is always "MEX" (ISO 3166-1 alpha-3). */
public record FiscalAddress(
        String street,
        String exteriorNumber,
        String interiorNumber,
        String neighborhood,
        String municipality,
        MexicanState state,
        MxZipCode zipCode,
        String country
) {
    public static final String MEXICO = "MEX";

    public FiscalAddress {
        street = required(street, "street", 150);
        exteriorNumber = required(exteriorNumber, "exteriorNumber", 20);
        interiorNumber = optional(interiorNumber, "interiorNumber", 20);
        neighborhood = required(neighborhood, "neighborhood", 100);
        municipality = required(municipality, "municipality", 100);
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(zipCode, "zipCode");
        country = country == null ? MEXICO : country.strip();
        if (!MEXICO.equals(country)) {
            throw new DomainValidationException("account.fiscal.address.country");
        }
    }

    private static String required(String value, String field, int max) {
        String trimmed = optional(value, field, max);
        if (trimmed == null) {
            throw new DomainValidationException("account.validation.required", field);
        }
        return trimmed;
    }

    private static String optional(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.strip();
        if (trimmed.length() > max) {
            throw new DomainValidationException("account.validation.maxLength", field, max);
        }
        return trimmed;
    }
}
