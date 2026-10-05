package com.ninsky.cronos.finance.currency;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Picker shape (spec §9.1). */
public record CurrencyOption(
        Long id,
        String code,
        String name,
        String symbol,
        int decimalPlaces,
        SymbolPosition symbolPosition,
        @JsonProperty("isDefault") boolean isDefault
) {

    public static CurrencyOption of(CurrencyEntity entity) {
        return new CurrencyOption(entity.getId(), entity.getCode(), entity.getName(), entity.getSymbol(), entity.getDecimalPlaces(),
                entity.getSymbolPosition(), entity.isDefault());
    }
}
