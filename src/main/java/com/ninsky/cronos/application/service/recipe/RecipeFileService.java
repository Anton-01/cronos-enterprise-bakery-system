package com.ninsky.cronos.application.service.recipe;

import com.ninsky.cronos.application.response.recipe.RecipeFileResponse;
import com.ninsky.cronos.infrastructure.storage.StoragePort;
import com.ninsky.cronos.domain.model.auth.User;
import com.ninsky.cronos.domain.model.recipe.Recipe;
import com.ninsky.cronos.domain.model.recipe.RecipeFile;
import com.ninsky.cronos.infrastructure.exception.BusinessException;
import com.ninsky.cronos.infrastructure.exception.ResourceNotFoundException;
import com.ninsky.cronos.domain.port.auth.UserRepositoryPort;
import com.ninsky.cronos.domain.port.recipe.RecipeFileRepositoryPort;
import com.ninsky.cronos.domain.port.recipe.RecipeRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecipeFileService {

    private final RecipeFileRepositoryPort recipeFileRepository;
    private final RecipeRepositoryPort recipeRepository;
    private final UserRepositoryPort userRepository;
    private final StoragePort cloudStorageService;

    @Transactional
    public RecipeFileResponse uploadRecipeFile(String username, UUID recipeId, MultipartFile file, String description) {
        log.info("User {} uploading a file to recipe {}", username, recipeId);

        User user = userRepository.findByUsername(username).orElseThrow();
        Recipe recipe = recipeRepository.findByIdAndUserId(recipeId, user.getId()).orElseThrow(() -> new ResourceNotFoundException("Receta no encontrada o sin acceso"));

        if (file.isEmpty()) throw new BusinessException("El archivo no puede estar vacío");

        String fileCategory = file.getContentType() != null && file.getContentType().startsWith("image/") ? "IMAGE" : "DOCUMENT";

        String folderPath = "recipes/" + recipeId.toString();
        String gcpFilePath;
        try {
            gcpFilePath = cloudStorageService.uploadFile(file, folderPath);
        } catch (java.io.IOException e) {
            log.error("Error uploading the file to GCP for recipe {}: {}", recipeId, e.getMessage());
            throw new BusinessException("An error occurred while saving the file to the cloud.");
        }

        String generatedFileName = gcpFilePath.substring(gcpFilePath.lastIndexOf("/") + 1);

        boolean isFirstFile = recipeFileRepository.findByRecipeIdOrderByCreatedAtDesc(recipeId).isEmpty();

        RecipeFile recipeFile = RecipeFile.builder().recipeId(recipe.getId()).fileName(generatedFileName)
                .originalFileName(file.getOriginalFilename())
                .filePath(gcpFilePath) // Actual internal path in the GCP Bucket
                .storageProvider("GCP")
                .publicUrl(null) // None, because the URL will be dynamic and temporary
                .fileSize(file.getSize()).fileType(fileCategory)
                .mimeType(file.getContentType()).isPrimary(isFirstFile)
                .description(description).build();

        recipeFile = recipeFileRepository.save(recipeFile);
        return mapToResponse(recipeFile);
    }

    @Transactional(readOnly = true)
    public List<RecipeFileResponse> getFilesByRecipeId(String username, UUID recipeId) {
        User user = userRepository.findByUsername(username).orElseThrow();

        if (!recipeRepository.existsByIdAndUserId(recipeId, user.getId())) {
            throw new ResourceNotFoundException("Receta no encontrada o sin acceso");
        }

        return recipeFileRepository.findByRecipeIdOrderByCreatedAtDesc(recipeId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteRecipeFile(String username, UUID recipeId, UUID fileId) {
        log.info("User {} requested that the file {} be deleted from recipe {}", username, fileId, recipeId);

        User user = userRepository.findByUsername(username).orElseThrow();

        if (!recipeRepository.existsByIdAndUserId(recipeId, user.getId())) {
            throw new ResourceNotFoundException("Receta no encontrada o sin acceso");
        }

        RecipeFile recipeFile = recipeFileRepository.findByIdAndRecipeId(fileId, recipeId).orElseThrow(() -> new ResourceNotFoundException("Archivo no encontrado en esta receta"));

        // Physically delete from Google Cloud Storage
        try {
            boolean deletedFromCloud = cloudStorageService.deleteFile(recipeFile.getFilePath());
            if (!deletedFromCloud) {
                log.warn("The physical file {} was not found in GCP (it may have already been deleted).", recipeFile.getFilePath());
            }
        } catch (Exception e) {
            log.error("Communication error with GCP while attempting to delete {}: {}", recipeFile.getFilePath(), e.getMessage());
            throw new BusinessException("The file could not be deleted from the cloud at this time.");
        }

        boolean wasPrimary = recipeFile.isPrimary();

        recipeFileRepository.delete(recipeFile);

        // Force a flush so that the subsequent COUNT does not include this file
        recipeFileRepository.flush();

        // Reassign the main photo if necessary
        if (wasPrimary) {
            log.info("The main photo for the recipe {} has been removed. Looking for a replacement...", recipeId);
            List<RecipeFile> remainingFiles = recipeFileRepository.findByRecipeIdOrderByCreatedAtDesc(recipeId);

            // We find the first available image and set it as the main image
            remainingFiles.stream().filter(f -> "IMAGE".equals(f.getFileType()))
                    .findFirst().ifPresent(newPrimary -> {
                        newPrimary.setPrimary(true);
                        recipeFileRepository.save(newPrimary);
                        log.info("The image {} has been set as the main photo.", newPrimary.getId());
                    });
        }

        log.info("File successfully deleted from the database and the bucket.");
    }

    private RecipeFileResponse mapToResponse(RecipeFile file) {
        // Secure URL that expires in 60 minutes
        String temporalSignedUrl = cloudStorageService.generateSignedUrl(file.getFilePath(), 60);

        return RecipeFileResponse.builder().id(file.getId())
                .fileName(file.getOriginalFileName())
                .fileUrl(temporalSignedUrl)
                .fileType(file.getMimeType() + " - " + file.getFileType())
                .sizeBytes(file.getFileSize())
                .description(file.getDescription())
                .isPrimary(file.isPrimary())
                .createdAt(file.getCreatedAt()).build();
    }
}
