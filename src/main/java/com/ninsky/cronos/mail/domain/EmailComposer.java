package com.ninsky.cronos.mail.domain;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Applies variables to a catalog template and derives the plain-text part (required for deliverability).
 * Pure: no I/O, no framework.
 */
public final class EmailComposer {

    private EmailComposer() {
    }

    /**
     * Composes {@code template} in {@code locale}.
     *
     * @param variables declared variables (all required)
     * @param implicit  variables every template may use without declaring them ({@code brandName}, {@code supportEmail})
     * @throws IllegalArgumentException when a declared variable is missing (a programming error of the sender)
     */
    public static ComposedEmail compose(EmailTemplate template, String locale, Map<String, String> variables,
                                        Map<String, String> implicit) {
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
        String normalized = MailLocales.normalize(locale);
        LocalizedEmail content = template.content(normalized);
        List<EmailBlock> blocks = content.blocks().stream()
                .map(block -> block.map(text -> Placeholders.apply(text, values),
                        url -> Placeholders.safeUrl(Placeholders.apply(url, values))))
                .toList();
        String subject = Placeholders.apply(content.subject(), values);
        String preheader = Placeholders.apply(content.preheader(), values);
        return new ComposedEmail(template.key(), normalized, subject, preheader, blocks,
                PlainText.of(subject, blocks, values.getOrDefault("brandName", "")), template.transactional());
    }

    /** Plain-text alternative of the blocks. */
    static final class PlainText {

        private PlainText() {
        }

        static String of(String subject, List<EmailBlock> blocks, String brand) {
            StringBuilder out = new StringBuilder();
            for (EmailBlock block : blocks) {
                switch (block.type()) {
                    case HEADING -> out.append(block.text().toUpperCase(java.util.Locale.ROOT)).append("\n\n");
                    case PARAGRAPH, SIGNATURE -> out.append(block.text()).append("\n\n");
                    case CODE -> out.append("    ").append(block.text()).append("\n\n");
                    case CALLOUT -> out.append("> ").append(block.text()).append("\n\n");
                    case BUTTON -> out.append(block.label()).append(": ").append(block.url()).append("\n\n");
                    case LIST -> {
                        block.items().forEach(item -> out.append("- ").append(item).append('\n'));
                        out.append('\n');
                    }
                    case DETAILS -> {
                        block.rows().forEach(row -> out.append(row.label()).append(": ").append(row.value()).append('\n'));
                        out.append('\n');
                    }
                    case DIVIDER -> out.append("----------------------------------------\n\n");
                }
            }
            if (out.isEmpty()) {
                out.append(subject).append("\n\n");
            }
            return out.append("-- \n").append(brand).append('\n').toString();
        }
    }
}
