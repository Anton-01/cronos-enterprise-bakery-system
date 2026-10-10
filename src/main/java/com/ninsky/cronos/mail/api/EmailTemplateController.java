package com.ninsky.cronos.mail.api;

import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.iam.permission.Authorities;
import com.ninsky.cronos.iam.shared.ActorProvider;
import com.ninsky.cronos.infrastructure.web.StrictApiContract;
import com.ninsky.cronos.mail.application.EmailTemplateService;
import com.ninsky.cronos.mail.application.RenderedEmail;
import com.ninsky.cronos.mail.domain.EmailTemplateCatalog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Transactional email catalog for administrators (§7.3). */
@RestController
@StrictApiContract
@RequestMapping("/admin/email-templates")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin · Email templates", description = "Localised template catalog, server preview and test sends")
public class EmailTemplateController {

    private final EmailTemplateService service;
    private final ActorProvider actors;

    public EmailTemplateController(EmailTemplateService service, ActorProvider actors) {
        this.service = service;
        this.actors = actors;
    }

    @GetMapping
    @PreAuthorize(Authorities.NOTIFICATION_EMAIL_TEMPLATE_READ)
    @Operation(summary = "Email template catalog", description = "The same catalog the UI previews (mail/catalog.json).")
    public ResponseEntity<ApiResponse<EmailTemplateCatalog>> catalog() {
        return ResponseEntity.ok(ApiResponse.success(null, service.catalog()));
    }

    @PostMapping("/{key}/preview")
    @PreAuthorize(Authorities.NOTIFICATION_EMAIL_TEMPLATE_READ)
    @Operation(summary = "Render a template", description = "Variables not sent use their sample value.")
    public ResponseEntity<ApiResponse<RenderedEmail>> preview(@PathVariable String key, @Valid @RequestBody EmailPreviewRequest request) {
        return ResponseEntity.ok(ApiResponse.success(null, service.preview(key, request)));
    }

    @PostMapping("/{key}/test")
    @PreAuthorize(Authorities.NOTIFICATION_EMAIL_TEMPLATE_TEST_SEND)
    @Operation(summary = "Send a test email", description = "Subject prefixed [TEST]; to defaults to the caller; 20 per hour.")
    public ResponseEntity<ApiResponse<EmailTestSendResult>> test(@PathVariable String key, @Valid @RequestBody EmailTestSendRequest request) {
        return ResponseEntity.ok(ApiResponse.success(null, service.testSend(key, request, actors.require())));
    }
}
