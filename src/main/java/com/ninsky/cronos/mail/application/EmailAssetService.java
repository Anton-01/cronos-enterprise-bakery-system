package com.ninsky.cronos.mail.application;

import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.eventing.application.DomainEventPublisher;
import com.ninsky.cronos.iam.audit.AuditEvent;
import com.ninsky.cronos.iam.audit.AuditRecorder;
import com.ninsky.cronos.iam.audit.AuditTargets;
import com.ninsky.cronos.iam.shared.Actor;
import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.mail.api.EmailAssetResponse;
import com.ninsky.cronos.mail.domain.EmailTemplateEvents.EmailAssetUploadedEvent;
import com.ninsky.cronos.mail.infrastructure.EmailAssetCustomRepository;
import com.ninsky.cronos.mail.infrastructure.EmailAssetCustomRepository.AssetRow;
import com.ninsky.cronos.mail.infrastructure.EmailImageSanitizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Email images (§4.5, §4.6): sanitized, stored content-addressed under {@code mail/{sha256}.{ext}} and served from a
 * public, never-expiring URL. Re-uploading the same file returns the existing asset. Assets a saved template or the
 * settings point to are never deleted; never-referenced ones older than 30 days may be cleaned up.
 */
@Slf4j
@Service
public class EmailAssetService {

    static final String KEY_PREFIX = "mail/";
    /** Largest accepted upload (2 MB). */
    public static final int MAX_UPLOAD_BYTES = EmailImageSanitizer.MAX_BYTES;
    public static final Duration CLEANUP_AFTER = Duration.ofDays(30);

    private final EmailAssetCustomRepository repository;
    private final EmailAssetStorage storage;
    private final MailProperties properties;
    private final DomainEventPublisher events;
    private final AuditRecorder audit;
    private final Clock clock;

    public EmailAssetService(EmailAssetCustomRepository repository, EmailAssetStorage storage, MailProperties properties,
                             DomainEventPublisher events, AuditRecorder audit, Clock clock) {
        this.repository = repository;
        this.storage = storage;
        this.properties = properties;
        this.events = events;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Sanitizes and stores {@code upload}.
     *
     * @throws com.ninsky.cronos.infrastructure.exception.ApiException ASSET_INVALID_TYPE, ASSET_TOO_LARGE, ASSET_DIMENSIONS
     */
    @Transactional
    public EmailAssetResponse upload(UUID tenant, byte[] upload, Actor actor) {
        EmailImageSanitizer.Result image = EmailImageSanitizer.process(upload);
        String sha = sha256(image.bytes());
        var existing = repository.findBySha(tenant, sha);
        if (existing.isPresent()) {
            return response(existing.get());
        }
        AssetRow row = new AssetRow(UUID.randomUUID(), sha, KEY_PREFIX + sha + "." + image.type().extension(), image.type().contentType(),
                image.bytes().length, image.width(), image.height());
        // Object first: a row never points to a missing object (an orphan object is harmless, content-addressed).
        storage.put(row.storageKey(), image.bytes(), row.contentType());
        if (!repository.insert(tenant, row, actor.id(), clock.instant())) {
            return repository.findBySha(tenant, sha).map(this::response).orElseThrow();
        }
        events.publish(new EmailAssetUploadedEvent(tenant, row.id(), sha, row.contentType(), row.sizeBytes()));
        audit.record(AuditEvent.of(AuditAction.EMAIL_ASSET_UPLOADED, AuditTargets.EMAIL_ASSET, row.id(), row.storageKey())
                .params(Map.of("contentType", row.contentType(), "sizeBytes", row.sizeBytes()))
                .build());
        return response(row);
    }

    /**
     * Refuses an upload whose declared size is already over the limit, before its bytes are read into memory.
     *
     * @throws com.ninsky.cronos.infrastructure.exception.ApiException ASSET_TOO_LARGE
     */
    public void checkDeclaredSize(long bytes) {
        if (bytes > MAX_UPLOAD_BYTES) {
            throw ApiException.of(ApiErrorCode.ASSET_TOO_LARGE, "file", "mail.asset.tooLarge", MAX_UPLOAD_BYTES / (1024 * 1024));
        }
    }

    /** Flags every asset whose public URL appears in {@code texts} (block texts and links, settings URLs) as referenced. */
    @Transactional
    public void markReferenced(UUID tenant, Collection<String> texts) {
        String base = base();
        repository.markReferenced(tenant, texts.stream().filter(Objects::nonNull).map(String::strip)
                .filter(text -> text.startsWith(base))
                .map(text -> text.substring(base.length()))
                .filter(key -> key.startsWith(KEY_PREFIX) && key.indexOf('/', KEY_PREFIX.length()) < 0)
                .distinct().toList());
    }

    /** Deletes never-referenced assets older than {@link #CLEANUP_AFTER}; returns how many were removed. */
    @Transactional
    public int cleanup(int batch) {
        int removed = 0;
        for (AssetRow row : repository.unreferencedBefore(clock.instant().minus(CLEANUP_AFTER), batch)) {
            if (repository.deleteUnreferenced(row.id())) {
                try {
                    storage.delete(row.storageKey());
                    removed++;
                } catch (RuntimeException e) {
                    log.warn("Could not delete email asset object {}: {}", row.storageKey(), e.getMessage());
                }
            }
        }
        return removed;
    }

    private EmailAssetResponse response(AssetRow row) {
        return new EmailAssetResponse(row.id(), properties.assets().urlFor(row.storageKey()), row.contentType(), row.sizeBytes(), row.width(),
                row.height());
    }

    private String base() {
        String base = properties.assets().publicBaseUrl();
        return base.endsWith("/") ? base : base + "/";
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
