package com.ninsky.cronos.application.response.recipe;

import lombok.Builder;
import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record RecipeFileResponse(
        UUID id,
        String fileName, // Aquí enviaremos el originalFileName
        String fileUrl,  // Aquí enviaremos el publicUrl
        String fileType, // Aquí enviaremos el mimeType
        Long sizeBytes,  // Aquí enviaremos el fileSize
        String description,
        boolean isPrimary, // Extra útil para el frontend
        LocalDateTime createdAt
) {}
