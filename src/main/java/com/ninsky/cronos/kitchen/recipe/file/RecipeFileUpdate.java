package com.ninsky.cronos.kitchen.recipe.file;

import com.fasterxml.jackson.annotation.JsonProperty;

/** {@code PATCH /recipes/{id}/files/{fileId}}; null fields are left unchanged. */
public record RecipeFileUpdate(String description, @JsonProperty("isCover") Boolean isCover) {
}
