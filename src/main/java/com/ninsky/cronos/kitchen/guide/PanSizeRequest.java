package com.ninsky.cronos.kitchen.guide;

import java.math.BigDecimal;

/** {@code POST/PUT /baking-guide/pan-sizes}: the caller's own pan (§6.2 rules). */
public record PanSizeRequest(PanShape shape, String name, BigDecimal diameterCm, BigDecimal lengthCm, BigDecimal widthCm,
                             BigDecimal heightCm, BigDecimal volumeMl, Integer servings, String notes) {
}
