package com.ninsky.cronos.mail.infrastructure;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ninsky.cronos.mail.application.ContentValidator;
import com.ninsky.cronos.mail.domain.EmailBlock;
import com.ninsky.cronos.mail.domain.EmailLocale;
import com.ninsky.cronos.mail.domain.LocalizedEmail;
import com.ninsky.cronos.mail.domain.Placeholders;
import com.ninsky.cronos.mail.domain.TemplateDefaults;
import com.ninsky.cronos.mail.domain.TemplateDefinition;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Loads {@code mail/catalog.json} (the copy of the UI's {@code src/assets/email-templates/catalog.json}) once, as the
 * read-only defaults ({@code email.defaults}: never evicted, it is on the classpath). Startup fails when the file is
 * inconsistent: duplicate keys, a missing category or locale, a description missing a language, an empty block, or a
 * placeholder that is neither declared nor implicit.
 */
public final class ClasspathTemplateDefaults {

    static final String CATALOG = "mail/catalog.json";

    private ClasspathTemplateDefaults() {
    }

    /**
     * @throws IllegalStateException listing every inconsistency found
     */
    public static TemplateDefaults load(ObjectMapper json) {
        ObjectMapper reader = json.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        try (InputStream in = new ClassPathResource(CATALOG).getInputStream()) {
            TemplateDefaults defaults = reader.readValue(in, TemplateDefaults.class);
            validate(defaults);
            return defaults;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the email catalog " + CATALOG, e);
        }
    }

    static void validate(TemplateDefaults defaults) {
        List<String> problems = new ArrayList<>();
        if (defaults.version() == null || defaults.version().isBlank()) {
            problems.add("version is required");
        }
        Set<String> keys = new HashSet<>();
        for (TemplateDefinition template : defaults.templates()) {
            if (!keys.add(template.key())) {
                problems.add("duplicate key " + template.key());
            }
            if (template.category() == null) {
                problems.add(template.key() + ": category is required");
            }
            if (template.description() == null || !template.description().complete()) {
                problems.add(template.key() + ": description needs es-MX and en");
            }
            template.variables().stream().filter(variable -> variable.description() == null || !variable.description().complete())
                    .forEach(variable -> problems.add(template.key() + ": variable " + variable.name() + " needs es-MX and en descriptions"));
            Set<String> allowed = new HashSet<>(template.variableNames());
            allowed.addAll(ContentValidator.IMPLICIT);
            for (EmailLocale locale : EmailLocale.values()) {
                LocalizedEmail content = template.content().get(locale);
                if (content == null) {
                    problems.add(template.key() + ": missing locale " + locale.tag());
                    continue;
                }
                Stream.concat(Stream.of(content.subject(), content.preheader()), content.blocks().stream().flatMap(EmailBlock::texts))
                        .flatMap(text -> Placeholders.referencedLoosely(text).stream())
                        .filter(name -> !allowed.contains(name))
                        .distinct()
                        .forEach(name -> problems.add(template.key() + "/" + locale.tag() + ": undeclared variable " + name));
                content.blocks().stream().filter(block -> !complete(block))
                        .forEach(block -> problems.add(template.key() + "/" + locale.tag() + ": incomplete " + block.type().json() + " block"));
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Email catalog is invalid: " + String.join("; ", problems));
        }
    }

    private static boolean complete(EmailBlock block) {
        return switch (block) {
            case EmailBlock.Heading b -> present(b.text());
            case EmailBlock.Paragraph b -> present(b.text());
            case EmailBlock.Code b -> present(b.text());
            case EmailBlock.Callout b -> present(b.text());
            case EmailBlock.Signature b -> present(b.text());
            case EmailBlock.Button b -> present(b.label()) && present(b.url());
            case EmailBlock.ListBlock b -> !b.items().isEmpty();
            case EmailBlock.Details b -> !b.rows().isEmpty();
            case EmailBlock.Image b -> present(b.url()) && b.alt() != null;
            case EmailBlock.Divider b -> true;
        };
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
