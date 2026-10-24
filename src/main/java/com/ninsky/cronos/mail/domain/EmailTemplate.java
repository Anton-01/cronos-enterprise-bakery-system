package com.ninsky.cronos.mail.domain;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One entry of the email catalog (§7.1).
 *
 * @param category      the key prefix ({@code auth}, {@code admin}, {@code quote}, {@code calendar}, {@code recipe}); groups
 *                      the catalog screen
 * @param transactional true for mail the user must receive (account, security, a client's own quote); those
 *                      ignore the EMAIL preference and carry no {@code List-Unsubscribe}
 * @param trigger       the domain event that sends it (documentation for the catalog screen)
 * @param content       by language: {@code es} and {@code en}
 */
public record EmailTemplate(String key, String category, MailAudience audience, boolean transactional, String trigger,
                            String description, List<TemplateVariable> variables, Map<String, LocalizedEmail> content) {

    public EmailTemplate {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(audience, "audience");
        variables = variables == null ? List.of() : List.copyOf(variables);
        content = content == null ? Map.of() : Map.copyOf(content);
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

    /** The content of {@code locale}, falling back to Spanish (the tenant language). */
    public LocalizedEmail content(String locale) {
        return Optional.ofNullable(content.get(MailLocales.contentKey(locale)))
                .or(() -> Optional.ofNullable(content.get(MailLocales.SPANISH_CONTENT)))
                .orElseThrow(() -> new IllegalStateException("Template " + key + " has no content"));
    }

    /** Sample values of every declared variable. */
    public Map<String, String> samples() {
        return variables.stream().collect(Collectors.toUnmodifiableMap(TemplateVariable::name, TemplateVariable::sample));
    }
}
