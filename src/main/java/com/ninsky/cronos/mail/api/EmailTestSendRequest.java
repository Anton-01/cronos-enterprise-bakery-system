package com.ninsky.cronos.mail.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Body of {@code POST /admin/email-templates/{key}/test}.
 *
 * @param to      defaults to the caller's own address; any other recipient must have an account here
 * @param content optional unsaved draft to send instead of the stored content (validated like a save)
 */
public record EmailTestSendRequest(@Pattern(regexp = "es-MX|en") String locale, @Email @Size(max = 254) String to,
                                   Map<String, String> variables, EmailTemplateContent content) {

    public EmailTestSendRequest {
        variables = variables == null ? Map.of() : Map.copyOf(variables);
    }
}
