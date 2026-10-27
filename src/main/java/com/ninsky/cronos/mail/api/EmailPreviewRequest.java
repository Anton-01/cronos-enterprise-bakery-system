package com.ninsky.cronos.mail.api;

import com.ninsky.cronos.mail.domain.EmailSettings;
import jakarta.validation.constraints.Pattern;

import java.util.Map;

/**
 * Body of {@code POST /admin/email-templates/{key}/preview}; missing variables take their sample value.
 *
 * @param content  optional draft rendered instead of the stored content (validated like a save, never persisted)
 * @param settings optional draft brand/sender (validated like a save, never persisted)
 */
public record EmailPreviewRequest(@Pattern(regexp = "es-MX|en") String locale, Map<String, String> variables,
                                  EmailTemplateContent content, EmailSettings settings) {

    public EmailPreviewRequest {
        variables = variables == null ? Map.of() : Map.copyOf(variables);
    }
}
