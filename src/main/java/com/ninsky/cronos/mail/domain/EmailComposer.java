package com.ninsky.cronos.mail.domain;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Applies variables to a template's content and derives the plain-text part (required for deliverability).
 * Pure: no I/O, no framework.
 */
public final class EmailComposer {

    private EmailComposer() {
    }

    /**
     * Composes the built-in content of {@code template} in {@code locale}.
     *
     * @see #compose(TemplateDefinition, EmailLocale, LocalizedEmail, Map, Map)
     */
    public static ComposedEmail compose(TemplateDefinition template, String locale, Map<String, String> variables,
                                        Map<String, String> implicit) {
        EmailLocale normalized = EmailLocale.normalize(locale);
        return compose(template, normalized, template.content(normalized), variables, implicit);
    }

    /**
     * Composes {@code content} (the effective content, or a draft) of {@code template}.
     *
     * @param variables declared variables (all required)
     * @param implicit  variables every template may use without declaring them ({@code brandName}, {@code supportEmail})
     * @throws IllegalArgumentException when a declared variable is missing (a programming error of the sender)
     */
    public static ComposedEmail compose(TemplateDefinition template, EmailLocale locale, LocalizedEmail content,
                                        Map<String, String> variables, Map<String, String> implicit) {
        Set<String> missing = template.variableNames().stream()
                .filter(name -> variables.get(name) == null)
                .collect(Collectors.toCollection(TreeSet::new));
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Template " + template.key() + " is missing variables " + missing);
        }
        Map<String, String> values = new HashMap<>(implicit);
        variables.forEach((name, value) -> {
            if (template.variableNames().contains(name)) {
                values.put(name, template.urlVariables().contains(name) ? Placeholders.safeUrl(value) : value);
            }
        });
        List<EmailBlock> blocks = content.blocks().stream()
                .map(block -> block.map(text -> Placeholders.apply(text, values),
                        url -> Placeholders.safeUrl(Placeholders.apply(url, values))))
                .toList();
        String subject = Placeholders.apply(content.subject(), values);
        String preheader = Placeholders.apply(content.preheader(), values);
        return new ComposedEmail(template.key(), locale.tag(), subject, preheader, blocks,
                PlainText.of(subject, blocks, values.getOrDefault("brandName", "")), template.transactional());
    }

    /** Plain-text alternative of the blocks. */
    static final class PlainText {

        private PlainText() {
        }

        static String of(String subject, List<EmailBlock> blocks, String brand) {
            StringBuilder out = new StringBuilder();
            for (EmailBlock block : blocks) {
                switch (block) {
                    case EmailBlock.Heading b -> out.append(b.text().toUpperCase(Locale.ROOT)).append("\n\n");
                    case EmailBlock.Paragraph b -> out.append(b.text()).append("\n\n");
                    case EmailBlock.Signature b -> out.append(b.text()).append("\n\n");
                    case EmailBlock.Code b -> {
                        out.append("    ").append(b.text()).append('\n');
                        if (b.caption() != null && !b.caption().isBlank()) {
                            out.append(b.caption()).append('\n');
                        }
                        out.append('\n');
                    }
                    case EmailBlock.Callout b -> out.append("> ").append(b.text()).append("\n\n");
                    case EmailBlock.Button b -> out.append(b.label()).append(": ").append(b.url()).append("\n\n");
                    case EmailBlock.ListBlock b -> {
                        b.items().forEach(item -> out.append("- ").append(item).append('\n'));
                        out.append('\n');
                    }
                    case EmailBlock.Details b -> {
                        b.rows().stream().filter(row -> row.value() != null && !row.value().isEmpty())
                                .forEach(row -> out.append(row.label()).append(": ").append(row.value()).append('\n'));
                        out.append('\n');
                    }
                    case EmailBlock.Image b -> {
                        String alt = b.alt() == null ? "" : b.alt();
                        if (!alt.isBlank() || b.href() != null) {
                            out.append('[').append(alt).append(']');
                            if (b.href() != null) {
                                out.append(" (").append(b.href()).append(')');
                            }
                            out.append("\n\n");
                        }
                    }
                    case EmailBlock.Divider b -> out.append("----------------------------------------\n\n");
                }
            }
            if (out.isEmpty()) {
                out.append(subject).append("\n\n");
            }
            return out.append("-- \n").append(brand).append('\n').toString();
        }
    }
}
