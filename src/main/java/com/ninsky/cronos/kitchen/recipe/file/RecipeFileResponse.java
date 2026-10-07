package com.ninsky.cronos.kitchen.recipe.file;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ninsky.cronos.finance.shared.UserRef;

import java.time.Instant;
import java.util.UUID;

/** §5.7 {@code RecipeFile}; {@code url}/{@code thumbnailUrl} are signed and short-lived. */
public record RecipeFileResponse(UUID id, String fileName, String url, String thumbnailUrl, FileKind kind, String mimeType, long sizeBytes,
                                 String description, @JsonProperty("isCover") boolean isCover, Instant uploadedAt, UserRef uploadedBy) {
}
