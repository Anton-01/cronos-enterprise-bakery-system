package com.ninsky.cronos.mail.domain;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The parsed {@code mail/catalog.json}: the built-in defaults of every transactional email, shared with the UI seed.
 *
 * @param version date-like tag of the shipped texts ({@code 2026-10-24}); part of the catalog ETag
 */
public record TemplateDefaults(String version, List<TemplateDefinition> templates) {

    public TemplateDefaults {
        templates = templates == null ? List.of() : List.copyOf(templates);
    }

    /** Templates by key. */
    public Map<String, TemplateDefinition> byKey() {
        return templates.stream().collect(Collectors.toUnmodifiableMap(TemplateDefinition::key, Function.identity()));
    }

    /** One template. */
    public Optional<TemplateDefinition> find(String key) {
        return templates.stream().filter(template -> template.key().equals(key)).findFirst();
    }
}
