package com.ninsky.cronos.mail.infrastructure;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.StorageOptions;
import com.ninsky.cronos.mail.application.EmailAssetStorage;
import com.ninsky.cronos.mail.application.MailProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/** Picks the {@link EmailAssetStorage} adapter from {@code app.mail.assets.storage} ({@code local} by default). */
@Configuration
public class EmailAssetStorageConfig {

    @Bean
    @ConditionalOnProperty(name = "app.mail.assets.storage", havingValue = "local", matchIfMissing = true)
    public EmailAssetStorage localEmailAssetStorage(MailProperties properties) {
        return new LocalEmailAssetStorage(Path.of(properties.assets().localDirectory()));
    }

    @Bean
    @ConditionalOnProperty(name = "app.mail.assets.storage", havingValue = "gcs")
    public EmailAssetStorage gcsEmailAssetStorage(MailProperties properties, @Value("${gcp.project-id}") String projectId,
                                                  @Value("${gcp.bucket-name}") String defaultBucket,
                                                  @Value("${gcp.credentials-file}") Resource credentials) throws IOException {
        try (InputStream credentialStream = credentials.getInputStream()) {
            var storage = StorageOptions.newBuilder().setProjectId(projectId)
                    .setCredentials(GoogleCredentials.fromStream(credentialStream)).build().getService();
            String bucket = properties.assets().gcsBucket() != null ? properties.assets().gcsBucket() : defaultBucket;
            return new GcsEmailAssetStorage(storage, bucket);
        }
    }
}
