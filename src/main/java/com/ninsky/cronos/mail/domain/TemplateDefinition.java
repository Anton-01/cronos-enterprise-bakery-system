package com.ninsky.cronos.mail.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One built-in template of {@code mail/catalog.json}: the read-only default. Administrators' edits are stored as
 * overrides per locale; the effective content is {@code override ?? default}. Variables are not editable: they are the
 * contract between the domain event and the template.
 *
 * @param transactional true for mail the user must receive (account, security, a client's own quote); those
 *                      ignore the EMAIL preference and carry no {@code List-Unsubscribe}
 * @param trigger       the domain event that sends it (documentation for the catalog screen)
 * @param content       the default content of every {@link EmailLocale}
 */
public record TemplateDefinition(String key, EmailCategory category, MailAudience audience, boolean transactional, String trigger,
                                 LocalizedText description, List<TemplateVariable> variables, Map<EmailLocale, LocalizedEmail> content) {

    public TemplateDefinition {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(audience, "audience");
        variables = variables == null ? List.of() : List.copyOf(variables);
        content = content == null || content.isEmpty() ? Map.of() : Collections.unmodifiableMap(new EnumMap<>(content));
    }

    /** Names of the declared variables. */
    public Set<String> variableNames() {
        return variables.stream().map(TemplateVariable::name).collect(Collectors.toUnmodifiableSet());
    }

    /** Names of the declared URL variables. */
    public Set<String> urlVariables() {
        return variables.stream().filter(v -> v.kind() == VariableKind.URL).map(TemplateVariable::name)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** The default content of {@code locale}, falling back to Spanish (the tenant language). */
    public LocalizedEmail content(EmailLocale locale) {
        return Optional.ofNullable(content.get(locale))
                .or(() -> Optional.ofNullable(content.get(EmailLocale.ES_MX)))
                .orElseThrow(() -> new IllegalStateException("Template " + key + " has no content"));
    }

    /** Sample values of every declared variable. */
    public Map<String, String> samples() {
        return variables.stream().collect(Collectors.toUnmodifiableMap(TemplateVariable::name, TemplateVariable::sample));
    }
}
