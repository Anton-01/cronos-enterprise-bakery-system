package com.ninsky.cronos.account.avatar.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;

/**
 * {@code app.avatars.*}.
 *
 * @param storage       {@code local} (filesystem, dev/test) or {@code gcs} (Google Cloud Storage, prod)
 * @param publicBaseUrl prefix of every avatar URL; the object key is appended. Point it at the CDN in
 *                      prod. The default serves avatars through {@code PublicAvatarController}.
 * @param localDirectory root directory for the {@code local} adapter
 * @param gcsBucket     bucket for the {@code gcs} adapter (defaults to {@code gcp.bucket-name})
 */
@ConfigurationProperties("app.avatars")
public record AvatarProperties(
        @DefaultValue("local") String storage,
        @DefaultValue("http://localhost:9191/api/v1/public") URI publicBaseUrl,
        @DefaultValue("./uploads") String localDirectory,
        String gcsBucket
) {
    public URI urlFor(String objectKey) {
        String base = publicBaseUrl.toString();
        return URI.create(base.endsWith("/") ? base + objectKey : base + "/" + objectKey);
    }
}
