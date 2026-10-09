package com.ninsky.cronos.kitchen.section;

/** {@code POST/PUT /recipe-sections}; {@code color} is {@code #RRGGBB} or null. */
public record RecipeSectionRequest(String name, String color) {
}
