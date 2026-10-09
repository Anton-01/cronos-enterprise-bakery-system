package com.ninsky.cronos.kitchen.guide;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Translation lookup order for one request (baking-studio §6.5): for each {@code Accept-Language} range by weight,
 * the exact tag, then its language; the authored es-MX content ends the search (any Spanish range stops there).
 */
public record GuideLanguage(List<String> candidates) {

    /** Content language of the base columns. */
    public static final String BASE = "es-MX";
    private static final int MAX_RANGES = 8;

    public GuideLanguage {
        candidates = List.copyOf(candidates);
    }

    public static GuideLanguage of(String acceptLanguage) {
        List<String> candidates = new ArrayList<>();
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return new GuideLanguage(candidates);
        }
        List<Locale.LanguageRange> ranges;
        try {
            ranges = Locale.LanguageRange.parse(acceptLanguage);
        } catch (IllegalArgumentException malformed) {
            return new GuideLanguage(candidates);
        }
        for (Locale.LanguageRange range : ranges.stream().limit(MAX_RANGES).toList()) {
            Locale locale = Locale.forLanguageTag(range.getRange());
            String language = locale.getLanguage();
            if (language.isEmpty() || "*".equals(range.getRange())) {
                continue;
            }
            if ("es".equals(language)) {
                break;
            }
            String exact = locale.getCountry().isEmpty() ? language : language + "-" + locale.getCountry();
            add(candidates, exact);
            add(candidates, language);
        }
        return new GuideLanguage(candidates);
    }

    /** The first candidate present in {@code translations}, or empty for the base content. */
    public <T> Optional<T> pick(Map<String, T> translations) {
        if (translations == null || translations.isEmpty()) {
            return Optional.empty();
        }
        return candidates.stream().map(translations::get).filter(java.util.Objects::nonNull).findFirst();
    }

    /** Stable key for caches and ETags. */
    public String key() {
        return candidates.isEmpty() ? BASE : String.join(",", candidates);
    }

    private static void add(List<String> candidates, String tag) {
        if (!candidates.contains(tag)) {
            candidates.add(tag);
        }
    }
}
