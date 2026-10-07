package com.ninsky.cronos.kitchen.shared;

/** Compact allergen reference used inside other shapes. */
public record AllergenRef(long id, String code, String name, String icon) {
}
