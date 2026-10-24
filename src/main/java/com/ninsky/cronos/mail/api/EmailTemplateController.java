package com.ninsky.cronos.mail.api;

import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.iam.permission.Authorities;
import com.ninsky.cronos.iam.shared.ActorProvider;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.infrastructure.web.StrictApiContract;
import com.ninsky.cronos.infrastructure.web.paging.CatalogPage;
import com.ninsky.cronos.mail.application.EmailAssetService;
import com.ninsky.cronos.mail.application.EmailContentProvider;
import com.ninsky.cronos.mail.application.EmailSettingsService;
import com.ninsky.cronos.mail.application.EmailTemplateCommandService;
import com.ninsky.cronos.mail.application.EmailTemplateQueryService;
import com.ninsky.cronos.mail.application.EmailTemplateQueryService.CatalogSnapshot;
import com.ninsky.cronos.mail.application.EmailTemplateService;
import com.ninsky.cronos.mail.application.RenderedEmail;
import com.ninsky.cronos.mail.domain.EmailLocale;
import com.ninsky.cronos.mail.domain.EmailSettings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;

/** Transactional email catalog for administrators (§7.3) and its customization (email-template-customization §4). */
@RestController
@StrictApiContract
@RequestMapping("/admin/email-templates")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin · Email templates", description = "Localised template catalog, editing, brand and sender, images, preview and test sends")
public class EmailTemplateController {

    static final String CATALOG_CACHE_CONTROL = "private, no-cache";

    private final EmailTemplateQueryService queries;
    private final EmailTemplateCommandService commands;
    private final EmailSettingsService settings;
    private final EmailAssetService assets;
    private final EmailTemplateService previews;
    private final EmailContentProvider provider;
    private final ActorProvider actors;

    public EmailTemplateController(EmailTemplateQueryService queries, EmailTemplateCommandService commands, EmailSettingsService settings,
                                   EmailAssetService assets, EmailTemplateService previews, EmailContentProvider provider,
                                   ActorProvider actors) {
        this.queries = queries;
        this.commands = commands;
        this.settings = settings;
        this.assets = assets;
        this.previews = previews;
        this.provider = provider;
        this.actors = actors;
    }

    @GetMapping
    @PreAuthorize(Authorities.NOTIFICATION_EMAIL_TEMPLATE_READ)
    @Operation(summary = "Email template catalog", description = "Effective content (override ?? default), brand and sender. "
            + "Weak ETag; If-None-Match answers 304 without a body.")
    public ResponseEntity<ApiResponse<EmailTemplateCatalogResponse>> catalog(
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        CatalogSnapshot snapshot = queries.catalog(acceptLanguage);
        if (matches(ifNoneMatch, snapshot.etag())) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(snapshot.etag())
                    .header(HttpHeaders.CACHE_CONTROL, CATALOG_CACHE_CONTROL).build();
        }
        return ResponseEntity.ok().eTag(snapshot.etag()).header(HttpHeaders.CACHE_CONTROL, CATALOG_CACHE_CONTROL)
                .body(ApiResponse.success(null, snapshot.catalog()));
    }

    @PutMapping("/{key}/content/{locale}")
    @PreAuthorize(Authorities.NOTIFICATION_EMAIL_TEMPLATE_MANAGE)
    @Operation(summary = "Save the content of a locale", description = "revision = the one the editor started from (null from the default). "
            + "409 STALE_REVISION on mismatch; content equal to the default deletes the override.")
    public ResponseEntity<ApiResponse<EmailTemplateResponse>> save(@PathVariable String key, @PathVariable String locale,
                                                                   @RequestBody EmailTemplateContent body) {
        return ResponseEntity.ok(ApiResponse.success(null, commands.save(key, locale(locale), body, actors.require())));
    }

    @DeleteMapping("/{key}/content/{locale}")
    @PreAuthorize(Authorities.NOTIFICATION_EMAIL_TEMPLATE_MANAGE)
    @Operation(summary = "Restore the default of a locale", description = "Idempotent.")
    public ResponseEntity<ApiResponse<EmailTemplateResponse>> reset(@PathVariable String key, @PathVariable String locale) {
        return ResponseEntity.ok(ApiResponse.success(null, commands.reset(key, locale(locale), actors.require())));
    }

    @GetMapping("/{key}/content/{locale}/history")
    @PreAuthorize(Authorities.NOTIFICATION_EMAIL_TEMPLATE_READ)
    @Operation(summary = "History of a locale", description = "Newest first; RESET entries carry no content.")
    public ResponseEntity<ApiResponse<CatalogPage<EmailTemplateHistoryEntry>>> history(@PathVariable String key, @PathVariable String locale,
                                                                                         @RequestParam(defaultValue = "0") int page,
                                                                                         @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(null, queries.history(key, locale(locale), page, size)));
    }

    @PutMapping("/settings")
    @PreAuthorize(Authorities.NOTIFICATION_EMAIL_TEMPLATE_MANAGE)
    @Operation(summary = "Save brand and sender", description = "Same revision rules as content. The From must use a verified domain.")
    public ResponseEntity<ApiResponse<EmailSettings>> saveSettings(@RequestBody EmailSettings body) {
        return ResponseEntity.ok(ApiResponse.success(null, settings.save(body, actors.require())));
    }

    @PostMapping(value = "/assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(Authorities.NOTIFICATION_EMAIL_TEMPLATE_MANAGE)
    @Operation(summary = "Upload an email image", description = "PNG, JPEG, GIF or WebP by magic bytes; ≤ 2 MB, ≤ 4000 × 4000 px. "
            + "Metadata stripped; the same file returns the same asset.")
    public ResponseEntity<ApiResponse<EmailAssetResponse>> upload(@RequestPart("file") MultipartFile file) throws IOException {
        assets.checkDeclaredSize(file.getSize());
        return ResponseEntity.ok(ApiResponse.success(null, assets.upload(provider.tenant(), file.getBytes(), actors.require())));
    }

    @PostMapping("/{key}/preview")
    @PreAuthorize(Authorities.NOTIFICATION_EMAIL_TEMPLATE_READ)
    @Operation(summary = "Render a template", description = "Variables not sent use their sample value; content/settings render a draft.")
    public ResponseEntity<ApiResponse<RenderedEmail>> preview(@PathVariable String key, @Valid @RequestBody EmailPreviewRequest request) {
        return ResponseEntity.ok(ApiResponse.success(null, previews.preview(key, request)));
    }

    @PostMapping("/{key}/test")
    @PreAuthorize(Authorities.NOTIFICATION_EMAIL_TEMPLATE_TEST_SEND)
    @Operation(summary = "Send a test email", description = "Subject prefixed [TEST]; to defaults to the caller; 20 per hour; content sends a draft.")
    public ResponseEntity<ApiResponse<EmailTestSendResult>> test(@PathVariable String key, @Valid @RequestBody EmailTestSendRequest request) {
        return ResponseEntity.ok(ApiResponse.success(null, previews.testSend(key, request, actors.require())));
    }

    /** {@code es-MX} or {@code en}, case-sensitive. */
    static EmailLocale locale(String segment) {
        return EmailLocale.fromTag(segment).orElseThrow(() -> ApiException.invalid("locale", "mail.locale.invalid", segment));
    }

    /** Weak comparison (RFC 9110 §13.1.2): {@code *} or any listed tag equal once {@code W/} is ignored. */
    static boolean matches(String ifNoneMatch, String etag) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return false;
        }
        String opaque = opaque(etag);
        return Arrays.stream(ifNoneMatch.split(",")).map(String::strip)
                .anyMatch(candidate -> candidate.equals("*") || opaque(candidate).equals(opaque));
    }

    private static String opaque(String tag) {
        return tag.startsWith("W/") ? tag.substring(2) : tag;
    }
}
