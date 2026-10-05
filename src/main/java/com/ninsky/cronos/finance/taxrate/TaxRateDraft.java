package com.ninsky.cronos.finance.taxrate;

import com.ninsky.cronos.finance.pricing.TaxFactorType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/** Normalised editable fields of an IVA rate. */
public record TaxRateDraft(String code, String name, String description, TaxFactorType factorType, BigDecimal ratePercent,
                           LocalDate validFrom, LocalDate validTo) {

    /** An applied rate is history: only name, description and validTo may change while in use (spec §10.2). */
    public List<String> immutableFieldsChangedFrom(TaxRateDraft current) {
        return Stream.of(
                        Objects.equals(code, current.code()) ? null : "code",
                        factorType == current.factorType() ? null : "factorType",
                        sameRate(ratePercent, current.ratePercent()) ? null : "ratePercent",
                        Objects.equals(validFrom, current.validFrom()) ? null : "validFrom")
                .filter(Objects::nonNull)
                .toList();
    }

    public static TaxRateDraft from(TaxRateRequest request) {
        String description = request.description() == null || request.description().isBlank() ? null : request.description().strip();
        return new TaxRateDraft(request.code().strip(), request.name().strip(), description, request.factorType(),
                request.ratePercent(), request.validFrom(), request.validTo());
    }

    static boolean sameRate(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }
}
