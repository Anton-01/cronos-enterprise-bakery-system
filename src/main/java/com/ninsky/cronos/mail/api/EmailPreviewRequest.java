package com.ninsky.cronos.mail.api;

import jakarta.validation.constraints.Pattern;

import java.util.Map;

/** Body of {@code POST /admin/email-templates/{key}/preview}; missing variables take their sample value. */
public record EmailPreviewRequest(@Pattern(regexp = "es-MX|en") String locale, Map<String, String> variables) {

    public EmailPreviewRequest {
        variables = variables == null ? Map.of() : Map.copyOf(variables);
    }
}
