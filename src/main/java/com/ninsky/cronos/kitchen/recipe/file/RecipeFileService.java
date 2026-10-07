package com.ninsky.cronos.kitchen.recipe.file;

import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.iam.audit.AuditEvent;
import com.ninsky.cronos.iam.audit.AuditRecorder;
import com.ninsky.cronos.iam.audit.AuditTargets;
import com.ninsky.cronos.iam.shared.ActorProvider;
import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.infrastructure.storage.StoragePort;
import com.ninsky.cronos.kitchen.recipe.RecipeAggregate;
import com.ninsky.cronos.kitchen.recipe.RecipeRevisions;
import com.ninsky.cronos.kitchen.recipe.RecipeCustomRepository;
import com.ninsky.cronos.kitchen.shared.KitchenProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Recipe attachments (§5.7, K6): content-verified, size/count/quota-limited, random keys, signed URLs. */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecipeFileService {

    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}\\p{Zl}\\p{Zp}]");
    private static final int MAX_NAME = 150;
    private static final int MAX_DESCRIPTION = 200;

    private final RecipeCustomRepository recipes;
    private final RecipeFileCustomRepository files;
    private final RecipeRevisions revisions;
    private final StoragePort storage;
    private final KitchenProperties properties;
    private final AuditRecorder audit;
    private final ActorProvider actors;
    private final Clock clock;

    @Transactional
    public RecipeFileResponse upload(UUID recipeId, MultipartFile upload, String description) {
        UUID tenant = actors.require().id();
        RecipeAggregate recipe = recipes.lockOwned(recipeId, tenant).orElseThrow(RecipeFileService::recipeNotFound);
        checkUpload(upload, description, 0);
        if (files.count(recipeId) >= properties.maxFilesPerRecipe()) {
            throw ApiException.of(ApiErrorCode.QUOTA_EXCEEDED, "file", "kitchen.file.tooMany", properties.maxFilesPerRecipe());
        }
        Content content = store(tenant, recipeId, upload);
        Instant now = clock.instant();
        UUID id = UUID.randomUUID();
        boolean cover = content.type().image() && !files.hasCover(recipeId);
        files.insert(new RecipeFileCustomRepository.NewFile(id, recipeId, content.key(), content.fileName(), content.type().kind(),
                content.type().mimeType(), content.size(), content.sha256(), blankToNull(description), cover, content.thumbnailKey(), tenant, now));
        revise(recipe, tenant, now, "kitchen.revision.fileUploaded", content.fileName(), Map.of("added", content.fileName()));
        audit.record(AuditEvent.of(AuditAction.RECIPE_FILE_UPLOADED, AuditTargets.RECIPE, recipeId, recipe.head().name())
                .params(Map.of("detail", content.fileName(), "kind", content.type().kind().name(), "sizeBytes", content.size()))
                .build());
        return files.find(recipeId, id).map(this::response).orElseThrow();
    }

    /**
     * Legacy {@code PUT /recipes/{id}/files}: new content for an existing file. Id and cover are kept (a non-image
     * hands the cover to the next image); the description changes only when sent. Old objects go after commit.
     */
    @Transactional
    public RecipeFileResponse replace(UUID recipeId, UUID fileId, MultipartFile upload, String description) {
        UUID tenant = actors.require().id();
        RecipeAggregate recipe = recipes.lockOwned(recipeId, tenant).orElseThrow(RecipeFileService::recipeNotFound);
        RecipeFileCustomRepository.Row old = files.find(recipeId, fileId).orElseThrow(RecipeFileService::fileNotFound);
        checkUpload(upload, description, old.sizeBytes());
        Content content = store(tenant, recipeId, upload);
        Instant now = clock.instant();
        files.replaceContent(new RecipeFileCustomRepository.NewFile(fileId, recipeId, content.key(), content.fileName(), content.type().kind(),
                content.type().mimeType(), content.size(), content.sha256(), description == null ? old.description() : blankToNull(description),
                old.cover() && content.type().image(), content.thumbnailKey(), tenant, now));
        if (old.cover() && !content.type().image()) {
            files.promoteCover(recipeId);
        } else if (content.type().image() && !files.hasCover(recipeId)) {
            files.makeCover(recipeId, fileId);
        }
        revise(recipe, tenant, now, "kitchen.revision.fileReplaced", content.fileName(),
                Map.of("replaced", Map.of("from", old.fileName(), "to", content.fileName())));
        audit.record(AuditEvent.of(AuditAction.RECIPE_FILE_UPLOADED, AuditTargets.RECIPE, recipeId, recipe.head().name())
                .params(Map.of("detail", content.fileName(), "kind", content.type().kind().name(), "sizeBytes", content.size(),
                        "replaced", old.fileName()))
                .build());
        deleteBlobsAfterCommit(old.storageKey(), old.thumbnailKey());
        return files.find(recipeId, fileId).map(this::response).orElseThrow();
    }

    @Transactional
    public RecipeFileResponse update(UUID recipeId, UUID fileId, RecipeFileUpdate request) {
        UUID tenant = actors.require().id();
        recipes.lockOwned(recipeId, tenant).orElseThrow(RecipeFileService::recipeNotFound);
        RecipeFileCustomRepository.Row file = files.find(recipeId, fileId).orElseThrow(RecipeFileService::fileNotFound);
        if (request.description() != null) {
            if (request.description().length() > MAX_DESCRIPTION) {
                throw ApiException.invalid("description", "api.validation.maxLength", MAX_DESCRIPTION);
            }
            files.updateDescription(fileId, blankToNull(request.description()));
        }
        if (Boolean.TRUE.equals(request.isCover())) {
            if (file.kind() != FileKind.IMAGE) {
                throw ApiException.invalid("isCover", "kitchen.file.coverImageOnly");
            }
            files.makeCover(recipeId, fileId);
        } else if (Boolean.FALSE.equals(request.isCover()) && file.cover()) {
            files.unsetCover(fileId);
        }
        return files.find(recipeId, fileId).map(this::response).orElseThrow();
    }

    @Transactional
    public void delete(UUID recipeId, UUID fileId) {
        UUID tenant = actors.require().id();
        RecipeAggregate recipe = recipes.lockOwned(recipeId, tenant).orElseThrow(RecipeFileService::recipeNotFound);
        RecipeFileCustomRepository.Row file = files.find(recipeId, fileId).orElseThrow(RecipeFileService::fileNotFound);
        files.delete(fileId);
        if (file.cover()) {
            files.promoteCover(recipeId);
        }
        Instant now = clock.instant();
        long version = recipes.bumpVersion(recipeId, tenant, now);
        revisions.write(recipeId, version, tenant, now, RecipeRevisions.Reason.of("kitchen.revision.fileDeleted", file.fileName()),
                Map.of("files", Map.of("removed", file.fileName())), recipe.head().cost().costPerUnit());
        audit.record(AuditEvent.of(AuditAction.RECIPE_FILE_DELETED, AuditTargets.RECIPE, recipeId, recipe.head().name())
                .params(Map.of("detail", file.fileName()))
                .build());
        deleteBlobsAfterCommit(file.storageKey(), file.thumbnailKey());
    }

    /** Stored, sanitized upload. */
    private record Content(SniffedFile type, String fileName, String key, String thumbnailKey, long size, String sha256) {
    }

    /** Presence, description, size and tenant quota ({@code freed} = bytes the upload replaces). */
    private void checkUpload(MultipartFile upload, String description, long freed) {
        if (upload == null || upload.isEmpty()) {
            throw ApiException.invalid("file", "api.validation.required");
        }
        if (description != null && description.length() > MAX_DESCRIPTION) {
            throw ApiException.invalid("description", "api.validation.maxLength", MAX_DESCRIPTION);
        }
        if (upload.getSize() > properties.maxFileSize().toBytes()) {
            throw ApiException.of(ApiErrorCode.PAYLOAD_TOO_LARGE, "file", "kitchen.file.tooLarge", properties.maxFileSize().toMegabytes());
        }
        UUID tenant = actors.require().id();
        if (files.tenantBytes(tenant) - freed + upload.getSize() > properties.tenantFileQuota().toBytes()) {
            throw ApiException.of(ApiErrorCode.QUOTA_EXCEEDED, "file", "kitchen.file.quota", properties.tenantFileQuota().toMegabytes());
        }
    }

    /** Sniffs, sanitizes and writes the objects under fresh random keys (removed again on rollback). */
    private Content store(UUID tenant, UUID recipeId, MultipartFile upload) {
        byte[] bytes = read(upload);
        String fileName = cleanName(upload.getOriginalFilename());
        SniffedFile type = FileSniffer.sniff(bytes, fileName)
                .orElseThrow(() -> ApiException.of(ApiErrorCode.UNSUPPORTED_MEDIA_TYPE, "file", "kitchen.file.unsupported"));
        byte[] stored = bytes;
        ImageSanitizer.Thumbnail thumbnail = null;
        if (type.image()) {
            ImageSanitizer.Result clean = ImageSanitizer.process(bytes, type);
            stored = clean.image();
            thumbnail = clean.thumbnail();
        }
        String base = "recipes/" + tenant + "/" + recipeId + "/" + UUID.randomUUID();
        String key = base + "." + type.extension();
        String thumbKey = thumbnail == null ? null : base + "_thumb." + thumbnail.extension();
        storage.put(key, stored, type.mimeType());
        if (thumbnail != null) {
            storage.put(thumbKey, thumbnail.bytes(), thumbnail.mimeType());
        }
        deleteBlobsOnRollback(key, thumbKey);
        return new Content(type, ensureExtension(fileName, type), key, thumbKey, stored.length, sha256(stored));
    }

    private void revise(RecipeAggregate recipe, UUID tenant, Instant now, String reasonKey, String fileName, Map<String, Object> change) {
        UUID recipeId = recipe.head().id();
        long version = recipes.bumpVersion(recipeId, tenant, now);
        revisions.write(recipeId, version, tenant, now, RecipeRevisions.Reason.of(reasonKey, fileName), Map.of("files", change),
                recipe.head().cost().costPerUnit());
    }

    public List<RecipeFileResponse> list(UUID recipeId) {
        return files.list(recipeId).stream().map(this::response).toList();
    }

    public RecipeFileResponse response(RecipeFileCustomRepository.Row row) {
        int minutes = properties.signedUrlMinutes();
        return new RecipeFileResponse(row.id(), row.fileName(), signed(row.storageKey(), minutes), signed(row.thumbnailKey(), minutes),
                row.kind(), row.mimeType(), row.sizeBytes(), row.description(), row.cover(), row.uploadedAt(), row.uploadedBy());
    }

    public String signed(String key, int minutes) {
        if (key == null) {
            return null;
        }
        try {
            return storage.generateSignedUrl(key, minutes);
        } catch (RuntimeException unavailable) {
            log.warn("Could not sign recipe file URL: {}", unavailable.getClass().getSimpleName());
            return null;
        }
    }

    /** Removes the objects once the deleting transaction committed (no dangling rows on rollback). */
    public void deleteBlobsAfterCommit(String... keys) {
        Runnable delete = () -> Stream.of(keys).filter(Objects::nonNull).forEach(this::quietDelete);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    delete.run();
                }
            });
        } else {
            delete.run();
        }
    }

    private void deleteBlobsOnRollback(String... keys) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    Stream.of(keys).filter(Objects::nonNull).forEach(RecipeFileService.this::quietDelete);
                }
            }
        });
    }

    private void quietDelete(String key) {
        try {
            storage.deleteFile(key);
        } catch (RuntimeException e) {
            log.warn("Could not delete recipe file object: {}", e.getClass().getSimpleName());
        }
    }

    /** Strips any path, control characters and surrounding blanks; keeps ≤ 150 chars with the extension. */
    static String cleanName(String original) {
        String name = original == null ? "" : original;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = CONTROL.matcher(name).replaceAll("").strip();
        if (name.isEmpty() || ".".equals(name) || "..".equals(name)) {
            name = "file";
        }
        if (name.length() > MAX_NAME) {
            String extension = FileSniffer.extension(name);
            int keep = MAX_NAME - (extension.isEmpty() ? 0 : extension.length() + 1);
            name = name.substring(0, keep).strip() + (extension.isEmpty() ? "" : "." + extension);
        }
        return name;
    }

    private static String ensureExtension(String name, SniffedFile type) {
        String extension = FileSniffer.extension(name);
        boolean matches = extension.equals(type.extension()) || ("jpg".equals(type.extension()) && "jpeg".equals(extension));
        if (matches) {
            return name;
        }
        String base = name.length() + type.extension().length() + 1 > MAX_NAME ? name.substring(0, MAX_NAME - type.extension().length() - 1) : name;
        return base + "." + type.extension();
    }

    private static byte[] read(MultipartFile upload) {
        try {
            return upload.getBytes();
        } catch (IOException e) {
            throw ApiException.invalid("file", "kitchen.file.corrupt");
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static ApiException recipeNotFound() {
        return ApiException.notFound("kitchen.recipe.notFound");
    }

    private static ApiException fileNotFound() {
        return ApiException.notFound("kitchen.file.notFound");
    }
}
