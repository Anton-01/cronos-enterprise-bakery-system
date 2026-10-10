package com.ninsky.cronos.mail.domain;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The template catalog shared with the UI (§7.1): the single source of every transactional email. */
public record EmailTemplateCatalog(int version, List<EmailTemplate> templates) {

    public EmailTemplateCatalog {
        templates = templates == null ? List.of() : List.copyOf(templates);
    }

    /** Templates by key. */
    public Map<String, EmailTemplate> byKey() {
        return templates.stream().collect(Collectors.toUnmodifiableMap(EmailTemplate::key, Function.identity()));
    }

    /** One template. */
    public Optional<EmailTemplate> find(String key) {
        return templates.stream().filter(template -> template.key().equals(key)).findFirst();
    }
}
