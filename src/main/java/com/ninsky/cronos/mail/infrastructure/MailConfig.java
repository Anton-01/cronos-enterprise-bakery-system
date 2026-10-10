package com.ninsky.cronos.mail.infrastructure;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ninsky.cronos.mail.application.MailProperties;
import com.ninsky.cronos.mail.domain.BlockType;
import com.ninsky.cronos.mail.domain.EmailBlock;
import com.ninsky.cronos.mail.domain.EmailTemplate;
import com.ninsky.cronos.mail.domain.EmailTemplateCatalog;
import com.ninsky.cronos.mail.domain.LocalizedEmail;
import com.ninsky.cronos.mail.domain.MailLocales;
import com.ninsky.cronos.mail.domain.Placeholders;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
 * Loads {@code mail/catalog.json} (the copy of the UI's {@code src/assets/email-templates/catalog.json}) and fails
 * startup when it is inconsistent: duplicate keys, a missing locale, a block without its fields, or a placeholder
 * that is neither declared nor implicit.
 */
@Configuration
@EnableConfigurationProperties(MailProperties.class)
public class MailConfig {

    static final String CATALOG = "mail/catalog.json";
    static final Set<String> IMPLICIT = Set.of("brandName", "supportEmail");

    @Bean
    public EmailTemplateCatalog emailTemplateCatalog(ObjectMapper json) {
        ObjectMapper reader = json.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        try (InputStream in = new ClassPathResource(CATALOG).getInputStream()) {
            EmailTemplateCatalog catalog = reader.readValue(in, EmailTemplateCatalog.class);
            validate(catalog);
            return catalog;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the email catalog " + CATALOG, e);
        }
    }

    /**
     * @throws IllegalStateException listing every inconsistency found
     */
    static void validate(EmailTemplateCatalog catalog) {
        List<String> problems = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (EmailTemplate template : catalog.templates()) {
            if (!keys.add(template.key())) {
                problems.add("duplicate key " + template.key());
            }
            for (String locale : MailLocales.ALL) {
                LocalizedEmail content = template.locales().get(locale);
                if (content == null) {
                    problems.add(template.key() + ": missing locale " + locale);
                    continue;
                }
                Set<String> allowed = new HashSet<>(template.variableNames());
                allowed.addAll(IMPLICIT);
                Stream.concat(Stream.of(content.subject(), content.preheader()), content.blocks().stream().flatMap(MailConfig::texts))
                        .flatMap(text -> Placeholders.referenced(text).stream())
                        .filter(name -> !allowed.contains(name))
                        .distinct()
                        .forEach(name -> problems.add(template.key() + "/" + locale + ": undeclared variable " + name));
                content.blocks().stream().filter(block -> !complete(block))
                        .forEach(block -> problems.add(template.key() + "/" + locale + ": incomplete " + block.type().json() + " block"));
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Email catalog is invalid: " + String.join("; ", problems));
        }
    }

    private static Stream<String> texts(EmailBlock block) {
        return Stream.of(Stream.of(block.text(), block.label(), block.url()), block.items().stream(),
                        block.rows().stream().flatMap(row -> Stream.of(row.label(), row.value())))
                .flatMap(s -> s);
    }

    private static boolean complete(EmailBlock block) {
        return switch (block.type()) {
            case HEADING, PARAGRAPH, CODE, CALLOUT, SIGNATURE -> block.text() != null && !block.text().isBlank();
            case BUTTON -> block.label() != null && block.url() != null;
            case LIST -> !block.items().isEmpty();
            case DETAILS -> !block.rows().isEmpty();
            case DIVIDER -> block.type() == BlockType.DIVIDER;
        };
    }
}
