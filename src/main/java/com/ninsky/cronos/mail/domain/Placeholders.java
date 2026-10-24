package com.ninsky.cronos.mail.domain;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * {@code {{ name }}} substitution. Values are inserted raw here; HTML escaping is the renderer's job (Thymeleaf
 * {@code th:text}), so text can never inject markup. Links must start with {@code https://}, {@code http://} or
 * {@code mailto:}; anything else (e.g. {@code javascript:}) becomes {@code #}.
 */
public final class Placeholders {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z][A-Za-z0-9_]*)\\s*}}");
    /** Any {@code {{ … }}} run, well-formed or not (an editor typo must not slip through as literal text). */
    private static final Pattern ANY_BRACES = Pattern.compile("\\{\\{(.*?)}}");

    private Placeholders() {
    }

    /** Replaces every placeholder; unknown names become empty text. */
    public static String apply(String text, Map<String, String> values) {
        if (text == null || text.indexOf('{') < 0) {
            return text;
        }
        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuilder out = new StringBuilder(text.length() + 32);
        while (matcher.find()) {
            String value = values.getOrDefault(matcher.group(1), "");
            matcher.appendReplacement(out, Matcher.quoteReplacement(value == null ? "" : value));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** Names referenced by {@code text}. */
    public static Set<String> referenced(String text) {
        if (text == null) {
            return Set.of();
        }
        return PLACEHOLDER.matcher(text).results().map(result -> result.group(1)).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Every {@code {{ … }}} of {@code text} as written, trimmed: a valid name, or garbage such as {@code "user name"}
     * that the caller reports as an unknown variable.
     */
    public static Set<String> referencedLoosely(String text) {
        if (text == null) {
            return Set.of();
        }
        return ANY_BRACES.matcher(text).results().map(result -> result.group(1).strip()).collect(Collectors.toUnmodifiableSet());
    }

    /** The variable name when {@code text} is exactly one placeholder ({@code "{{ resetUrl }}"}), otherwise empty. */
    public static Optional<String> single(String text) {
        if (text == null) {
            return Optional.empty();
        }
        Matcher matcher = PLACEHOLDER.matcher(text.strip());
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    /** True when any placeholder remains (a programming error once variables are applied). */
    public static boolean hasPlaceholder(String text) {
        return text != null && PLACEHOLDER.matcher(text).find();
    }

    /** {@code url} when its scheme is allowed, otherwise {@code #}. */
    public static String safeUrl(String url) {
        if (url == null) {
            return "#";
        }
        String trimmed = url.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        return lower.startsWith("https://") || lower.startsWith("http://") || lower.startsWith("mailto:") ? trimmed : "#";
    }
}
