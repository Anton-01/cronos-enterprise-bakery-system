package com.ninsky.cronos.infrastructure.storage;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class GcsStorageAdapter implements StoragePort {

    @Value("${gcp.project-id}")
    private String projectId;

    @Value("${gcp.bucket-name}")
    private String bucketName;

    @Value("${gcp.credentials-file}")
    private Resource credentialsResource;

    private Storage storage;

    @PostConstruct
    private void init() throws IOException {
        log.info("Starting connection with Google Cloud Storage ...");
        this.storage = StorageOptions.newBuilder().setProjectId(projectId).setCredentials(GoogleCredentials.fromStream(credentialsResource.getInputStream()))
                .build().getService();
    }

    /**
     * Upload a file by organizing it into virtual folders.
     * @param file The Multipart file.
     * @param folder The destination folder (e.g., “recipes/uuid”, “users/avatars”).
     * @return The internal name (filePath) used to store it in the database.
     */
    @Override
    public String uploadFile(MultipartFile file, String folder) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : "";

        String storedFileName = folder + "/" + UUID.randomUUID() + extension;

        BlobId blobId = BlobId.of(bucketName, storedFileName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType(file.getContentType()).build();

        storage.create(blobInfo, file.getBytes());
        log.info("File uploaded to GCP: {}", storedFileName);

        return storedFileName;
    }

    @Override
    public boolean deleteFile(String filePath) {
        BlobId blobId = BlobId.of(bucketName, filePath);
        boolean deleted = storage.delete(blobId);
        if (deleted) {
            log.info("File deleted from GCP: {}", filePath);
        }
        return deleted;
    }

    /**
     * Generate a secure temporary URL.
     * Use it when viewing the recipe or opening a shared link.
     * @param filePath The path to the file within the bucket.
     * @param expirationMinutes URL lifetime.
     * @return A public URL that expires automatically.
     */
    @Override
    public String generateSignedUrl(String filePath, int expirationMinutes) {
        if (filePath == null || filePath.trim().isEmpty()) return null;

        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucketName, filePath)).build();
        URL signedUrl = storage.signUrl(blobInfo, expirationMinutes, TimeUnit.MINUTES, Storage.SignUrlOption.withV4Signature());

        return signedUrl.toString();
    }
}
