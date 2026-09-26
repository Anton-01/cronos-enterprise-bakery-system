package com.ninsky.cronos.account.avatar.infrastructure;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.StorageOptions;
import com.ninsky.cronos.account.avatar.application.port.AvatarStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/** Picks the {@link AvatarStorage} adapter from {@code app.avatars.storage} ({@code local} by default). */
@Configuration
public class AvatarStorageConfig {

    @Bean
    @ConditionalOnProperty(name = "app.avatars.storage", havingValue = "local", matchIfMissing = true)
    public AvatarStorage localFilesystemAvatarStorage(AvatarProperties properties) {
        return new LocalFilesystemAvatarStorage(Path.of(properties.localDirectory()), key -> properties.urlFor(key.value()));
    }

    @Bean
    @ConditionalOnProperty(name = "app.avatars.storage", havingValue = "gcs")
    public AvatarStorage gcsAvatarStorage(AvatarProperties properties,
                                          @Value("${gcp.project-id}") String projectId,
                                          @Value("${gcp.bucket-name}") String defaultBucket,
                                          @Value("${gcp.credentials-file}") Resource credentials) throws IOException {
        try (InputStream credentialStream = credentials.getInputStream()) {
            var storage = StorageOptions.newBuilder()
                    .setProjectId(projectId)
                    .setCredentials(GoogleCredentials.fromStream(credentialStream))
                    .build()
                    .getService();
            String bucket = properties.gcsBucket() != null ? properties.gcsBucket() : defaultBucket;
            return new GcsAvatarStorage(storage, bucket, key -> properties.urlFor(key.value()));
        }
    }
}
