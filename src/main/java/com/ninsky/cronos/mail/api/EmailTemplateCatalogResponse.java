package com.ninsky.cronos.mail.api;

import com.ninsky.cronos.mail.domain.EmailBrand;
import com.ninsky.cronos.mail.domain.EmailSender;

import java.util.List;

/**
 * {@code GET /admin/email-templates}.
 *
 * @param version          version of the built-in defaults ({@code 2026-10-24})
 * @param settingsRevision revision of the saved settings, null while the defaults are served
 */
public record EmailTemplateCatalogResponse(String version, EmailBrand brand, EmailSender sender, Long settingsRevision,
                                           List<EmailTemplateResponse> templates) {

    public EmailTemplateCatalogResponse {
        templates = List.copyOf(templates);
    }
}
