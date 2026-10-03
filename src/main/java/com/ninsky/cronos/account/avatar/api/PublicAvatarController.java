package com.ninsky.cronos.account.avatar.api;

import com.ninsky.cronos.account.avatar.application.port.AvatarStorage;
import com.ninsky.cronos.account.avatar.domain.AvatarKey;
import com.ninsky.cronos.account.shared.domain.DomainValidationException;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Serves avatar objects when {@code app.avatars.public-base-url} points at the API (dev, or a CDN
 * whose origin is the API). Under {@code /public/**}, which {@code SecurityConfig} already permits:
 * {@code <img src>} requests carry no bearer token. Keys are unguessable (content hash) and immutable,
 * hence the one-year {@code immutable} caching.
 */
@Hidden
@RestController
@RequiredArgsConstructor
public class PublicAvatarController {

    static final String IMMUTABLE_CACHE_CONTROL = "public, max-age=31536000, immutable";

    private final AvatarStorage avatarStorage;

    @GetMapping(value = "/public/avatars/{userId}/{fileName:.+}", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> avatar(@PathVariable UUID userId, @PathVariable String fileName) {
        AvatarKey key;
        try {
            key = AvatarKey.of(userId, fileName);
        } catch (DomainValidationException e) {
            return ResponseEntity.notFound().build();
        }
        return avatarStorage.read(key)
                .map(bytes -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        .header(HttpHeaders.CACHE_CONTROL, IMMUTABLE_CACHE_CONTROL)
                        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(key.fileName()).build().toString())
                        .header("X-Content-Type-Options", "nosniff")
                        .body(bytes))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
