package com.ninsky.cronos.domain.model.recipe;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipeFile {
    private UUID id;
    private UUID recipeId;
    private String fileName;
    private String originalFileName;
    private String filePath;
    @Builder.Default
    private String storageProvider = "LOCAL";
    private String publicUrl;
    private Long fileSize;
    private String fileType;
    private String mimeType;
    @Builder.Default
    private boolean isPrimary = false;
    private String thumbnailPath;
    private String description;
    @Builder.Default
    private Long version = 0L;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
