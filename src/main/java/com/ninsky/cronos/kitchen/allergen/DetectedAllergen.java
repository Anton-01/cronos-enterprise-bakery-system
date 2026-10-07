package com.ninsky.cronos.kitchen.allergen;

/** One detection suggestion: the allergen and the longest keyword that matched. */
public record DetectedAllergen(long allergenId, String code, String name, String keyword) {
}
