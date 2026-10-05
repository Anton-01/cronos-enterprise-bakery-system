package com.ninsky.cronos.finance.taxrate;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ninsky.cronos.finance.pricing.TaxFactorType;

import java.math.BigDecimal;

/** Picker shape (spec §10.1). */
public record TaxRateOption(
        Long id,
        String code,
        String name,
        TaxFactorType factorType,
        BigDecimal ratePercent,
        @JsonProperty("isDefault") boolean isDefault
) {

    public static TaxRateOption of(TaxRateEntity entity) {
        return new TaxRateOption(entity.getId(), entity.getCode(), entity.getName(), entity.getFactorType(), entity.getRatePercent(),
                entity.isDefault());
    }
}
