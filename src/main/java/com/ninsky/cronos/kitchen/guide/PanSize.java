package com.ninsky.cronos.kitchen.guide;

import java.math.BigDecimal;
import java.util.UUID;

/** A pan size (§6.1); unused dimensions are null ({@code widthCm = lengthCm} for SQUARE). */
public record PanSize(UUID id, String code, GuideScope scope, PanShape shape, String name, BigDecimal diameterCm, BigDecimal lengthCm,
                      BigDecimal widthCm, BigDecimal heightCm, BigDecimal volumeMl, Integer servings, String notes) {
}
