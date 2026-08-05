package com.ninsky.cronos.infrastructure.persistence.recipe.mapper;

import com.ninsky.cronos.domain.model.recipe.RecipeFile;
import com.ninsky.cronos.infrastructure.persistence.recipe.entity.RecipeFileJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class RecipeFileMapper {

    public RecipeFile toDomain(RecipeFileJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return RecipeFile.builder()
                .id(entity.getId())
                .recipeId(entity.getRecipeId())
                .fileName(entity.getFileName())
                .originalFileName(entity.getOriginalFileName())
                .filePath(entity.getFilePath())
                .storageProvider(entity.getStorageProvider())
                .publicUrl(entity.getPublicUrl())
                .fileSize(entity.getFileSize())
                .fileType(entity.getFileType())
                .mimeType(entity.getMimeType())
                .isPrimary(entity.isPrimary())
                .thumbnailPath(entity.getThumbnailPath())
                .description(entity.getDescription())
                .version(entity.getVersion())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public RecipeFileJpaEntity toEntity(RecipeFile domain) {
        if (domain == null) {
            return null;
        }
        RecipeFileJpaEntity entity = RecipeFileJpaEntity.builder()
                .id(domain.getId())
                .recipeId(domain.getRecipeId())
                .fileName(domain.getFileName())
                .originalFileName(domain.getOriginalFileName())
                .filePath(domain.getFilePath())
                .storageProvider(domain.getStorageProvider())
                .publicUrl(domain.getPublicUrl())
                .fileSize(domain.getFileSize())
                .fileType(domain.getFileType())
                .mimeType(domain.getMimeType())
                .isPrimary(domain.isPrimary())
                .thumbnailPath(domain.getThumbnailPath())
                .description(domain.getDescription())
                .version(domain.getVersion())
                .build();
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
        return entity;
    }
}
