package com.ninsky.cronos.finance.currency;

import java.util.List;

/** Normalised editable fields of a currency. */
public record CurrencyDraft(String code, String numericCode, String name, String symbol, int decimalPlaces,
                           SymbolPosition symbolPosition) {

    /** Fields that are frozen once the currency is referenced by a document (spec §9.2). */
    public List<String> immutableFieldsChangedFrom(CurrencyDraft current) {
        return java.util.stream.Stream.of(
                        code.equals(current.code()) ? null : "code",
                        numericCode.equals(current.numericCode()) ? null : "numericCode",
                        decimalPlaces == current.decimalPlaces() ? null : "decimalPlaces")
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    public static CurrencyDraft from(CurrencyRequest request) {
        return new CurrencyDraft(trim(request.code()), trim(request.numericCode()), trim(request.name()), trim(request.symbol()),
                request.decimalPlaces(), request.symbolPosition());
    }

    private static String trim(String value) {
        return value == null ? null : value.strip();
    }
}
