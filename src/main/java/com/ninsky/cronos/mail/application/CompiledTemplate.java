package com.ninsky.cronos.mail.application;

import com.ninsky.cronos.mail.domain.EffectiveContent;
import com.ninsky.cronos.mail.domain.EmailSettings;
import com.ninsky.cronos.mail.domain.TemplateDefinition;

/**
 * Render plan of one template locale ({@code email.compiled}): definition, effective content and settings, already
 * resolved and validated, keyed by their revisions. The dispatcher composes and renders it without any database read;
 * the Thymeleaf layout itself is parsed once and kept by the template engine's own cache.
 */
public record CompiledTemplate(TemplateDefinition definition, EffectiveContent content, EmailSettings settings) {
}
