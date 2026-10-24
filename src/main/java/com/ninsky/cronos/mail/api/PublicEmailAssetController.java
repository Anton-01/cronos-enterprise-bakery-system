package com.ninsky.cronos.mail.api;

import com.ninsky.cronos.mail.application.EmailAssetStorage;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serves email images when {@code app.mail.assets.public-base-url} points at the API (dev, or a CDN whose origin is the
 * API). Under {@code /public/**}: mail clients and Gmail's image proxy fetch anonymously, without cookies or tokens.
 * Keys are content hashes and immutable, hence the one-year {@code immutable} caching.
 */
@Hidden
@RestController
public class PublicEmailAssetController {

    static final String IMMUTABLE_CACHE_CONTROL = "public, max-age=31536000, immutable";
    private static final Pattern FILE = Pattern.compile("^([0-9a-f]{64})\\.(png|jpg|gif|webp)$");
    private static final Map<String, MediaType> TYPES = Map.of("png", MediaType.IMAGE_PNG, "jpg", MediaType.IMAGE_JPEG,
            "gif", MediaType.IMAGE_GIF, "webp", MediaType.parseMediaType("image/webp"));

    private final EmailAssetStorage storage;

    public PublicEmailAssetController(EmailAssetStorage storage) {
        this.storage = storage;
    }

    @GetMapping("/public/mail-assets/mail/{fileName:.+}")
    public ResponseEntity<byte[]> asset(@PathVariable String fileName) {
        Matcher matcher = FILE.matcher(fileName.toLowerCase(Locale.ROOT));
        if (!matcher.matches() || !fileName.equals(fileName.toLowerCase(Locale.ROOT))) {
            return ResponseEntity.notFound().build();
        }
        return storage.read("mail/" + fileName)
                .map(bytes -> ResponseEntity.ok()
                        .contentType(TYPES.get(matcher.group(2)))
                        .header(HttpHeaders.CACHE_CONTROL, IMMUTABLE_CACHE_CONTROL)
                        .header("X-Content-Type-Options", "nosniff")
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                        .body(bytes))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
