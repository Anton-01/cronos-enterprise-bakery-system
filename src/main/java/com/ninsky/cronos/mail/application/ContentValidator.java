package com.ninsky.cronos.mail.application;

import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.Violations;
import com.ninsky.cronos.mail.domain.DetailRow;
import com.ninsky.cronos.mail.domain.EmailBlock;
import com.ninsky.cronos.mail.domain.LocalizedEmail;
import com.ninsky.cronos.mail.domain.Placeholders;
import com.ninsky.cronos.mail.domain.TemplateDefinition;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Validates edited content (§3.3) and collects every problem with its request-body path ({@code blocks[3].url}), so the
 * editor marks every bad block in one round trip. Every {@code {{ name }}} must be a declared variable or an implicit one
 * ({@code brandName}, {@code supportEmail}): that keeps "the sender supplies every declared variable" true after edits.
 * Preview and test sends of a draft go through the same checks.
 */
@Component
public class ContentValidator {

    /** Variables every template may use without declaring them. */
    public static final Set<String> IMPLICIT = Set.of("brandName", "supportEmail");
    static final Set<String> TONES = Set.of("info", "success", "warning", "danger");
    static final int MIN_IMAGE_WIDTH = 40;
    static final int MAX_IMAGE_WIDTH = 536;
    static final int MAX_BLOCKS = 40;

    private final MailProperties properties;

    public ContentValidator(MailProperties properties) {
        this.properties = properties;
    }

    /**
     * @throws com.ninsky.cronos.infrastructure.exception.ApiException VALIDATION_ERROR / UNKNOWN_VARIABLE, one entry per
     *                                                                 problem
     */
    public void validate(TemplateDefinition template, LocalizedEmail content) {
        Violations violations = new Violations();
        Set<String> allowed = new HashSet<>(template.variableNames());
        allowed.addAll(IMPLICIT);
        Context context = new Context(violations, Set.copyOf(allowed), template.urlVariables());

        context.length("subject", content.subject(), 1, 150);
        context.length("preheader", content.preheader(), 0, 200);
        List<EmailBlock> blocks = content.blocks();
        if (blocks.isEmpty() || blocks.size() > MAX_BLOCKS) {
            violations.invalid("blocks", "mail.content.count", 1, MAX_BLOCKS);
        }
        for (int i = 0; i < blocks.size(); i++) {
            block("blocks[" + i + "]", blocks.get(i), context);
        }
        violations.throwIfAny();
    }

    private void block(String path, EmailBlock block, Context c) {
        switch (block) {
            case null -> c.violations.invalid(path, "mail.content.unknownBlock");
            case EmailBlock.Heading b -> c.length(path + ".text", b.text(), 1, 200);
            case EmailBlock.Paragraph b -> c.length(path + ".text", b.text(), 1, 2000);
            case EmailBlock.Signature b -> c.length(path + ".text", b.text(), 1, 200);
            case EmailBlock.Button b -> {
                c.length(path + ".label", b.label(), 1, 60);
                c.link(path + ".url", b.url(), true);
            }
            case EmailBlock.Code b -> {
                c.length(path + ".text", b.text(), 1, 64);
                c.length(path + ".caption", b.caption(), 0, 120);
            }
            case EmailBlock.Details b -> {
                c.count(path + ".rows", b.rows().size(), 1, 12);
                for (int r = 0; r < b.rows().size(); r++) {
                    DetailRow row = b.rows().get(r);
                    String rowPath = path + ".rows[" + r + "]";
                    if (row == null) {
                        c.violations.invalid(rowPath, "mail.content.required");
                        continue;
                    }
                    c.length(rowPath + ".label", row.label(), 1, 60);
                    c.length(rowPath + ".value", row.value(), 0, 300);
                }
            }
            case EmailBlock.Callout b -> {
                c.violations.invalidIf(!TONES.contains(b.tone()), path + ".tone", "mail.content.tone");
                c.length(path + ".text", b.text(), 1, 600);
            }
            case EmailBlock.ListBlock b -> {
                c.count(path + ".items", b.items().size(), 1, 20);
                for (int n = 0; n < b.items().size(); n++) {
                    c.length(path + ".items[" + n + "]", b.items().get(n), 1, 300);
                }
            }
            case EmailBlock.Image b -> {
                image(path + ".url", b.url(), c);
                // Required for accessibility; empty is allowed (decorative image), null is not.
                if (b.alt() == null) {
                    c.violations.invalid(path + ".alt", "mail.content.required");
                } else {
                    c.length(path + ".alt", b.alt(), 0, 200);
                }
                if (b.width() != null && (b.width() < MIN_IMAGE_WIDTH || b.width() > MAX_IMAGE_WIDTH)) {
                    c.violations.invalid(path + ".width", "mail.content.width", MIN_IMAGE_WIDTH, MAX_IMAGE_WIDTH);
                }
                if (b.href() != null) {
                    c.link(path + ".href", b.href(), true);
                }
            }
            case EmailBlock.Divider b -> {
                // Nothing to check.
            }
        }
    }

    /** Image sources: {@code https://} only, and on the asset host when {@code app.mail.assets.enforce-host} is on. */
    private void image(String path, String url, Context c) {
        if (url == null || url.isBlank()) {
            c.violations.invalid(path, "mail.content.required");
            return;
        }
        String trimmed = url.strip();
        if (!trimmed.toLowerCase(Locale.ROOT).startsWith("https://") || trimmed.contains("{{")) {
            c.violations.invalid(path, "mail.content.httpsOnly");
            return;
        }
        String host = properties.assets().publicBaseUrl();
        if (properties.assets().enforceHost() && !trimmed.startsWith(host.endsWith("/") ? host : host + "/")) {
            c.violations.invalid(path, "mail.content.assetHost", host);
        }
    }

    /** Collected state of one validation run. */
    private record Context(Violations violations, Set<String> allowed, Set<String> urlVariables) {

        void length(String path, String value, int min, int max) {
            if (value == null) {
                if (min > 0) {
                    violations.invalid(path, "mail.content.required");
                }
                return;
            }
            int length = value.strip().length();
            if (min > 0 && length == 0) {
                violations.invalid(path, "mail.content.required");
            } else if (value.length() > max) {
                violations.invalid(path, min > 0 ? "mail.content.length" : "mail.content.maxLength",
                        min > 0 ? new Object[]{min, max} : new Object[]{max});
            }
            variables(path, value);
        }

        void count(String path, int size, int min, int max) {
            violations.invalidIf(size < min || size > max, path, "mail.content.count", min, max);
        }

        /** {@code https://…}, {@code mailto:…}, or exactly one {@code {{ var }}} of kind url. */
        void link(String path, String url, boolean required) {
            if (url == null || url.isBlank()) {
                if (required) {
                    violations.invalid(path, "mail.content.required");
                }
                return;
            }
            String trimmed = url.strip();
            String lower = trimmed.toLowerCase(Locale.ROOT);
            if (lower.startsWith("https://") || lower.startsWith("mailto:")) {
                variables(path, trimmed);
                return;
            }
            var single = Placeholders.single(trimmed);
            if (single.isPresent() && !allowed.contains(single.get())) {
                violations.add(ApiErrorCode.UNKNOWN_VARIABLE, path, "mail.content.unknownVariable", single.get());
            } else if (single.isEmpty() || !urlVariables.contains(single.get())) {
                violations.invalid(path, "mail.content.url");
            }
        }

        void variables(String path, String text) {
            Placeholders.referencedLoosely(text).stream().filter(name -> !allowed.contains(name)).sorted()
                    .forEach(name -> violations.add(ApiErrorCode.UNKNOWN_VARIABLE, path, "mail.content.unknownVariable", name));
        }
    }
}
