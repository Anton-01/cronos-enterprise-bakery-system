package com.ninsky.cronos.mail.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * What a template sends in one locale: the administrator's override, or the built-in default.
 *
 * @param revision  the override's revision, null when the default is served
 * @param updatedBy the override's author, null for the default
 */
public record EffectiveContent(String templateKey, EmailLocale locale, Long revision, LocalizedEmail content, Instant updatedAt,
                               UUID updatedBy) {

    public EffectiveContent {
        Objects.requireNonNull(templateKey, "templateKey");
        Objects.requireNonNull(locale, "locale");
        Objects.requireNonNull(content, "content");
    }

    /** The built-in content. */
    public static EffectiveContent ofDefault(TemplateDefinition template, EmailLocale locale) {
        return new EffectiveContent(template.key(), locale, null, template.content(locale), null, null);
    }

    /** True when an override is served. */
    public boolean customized() {
        return revision != null;
    }
}
