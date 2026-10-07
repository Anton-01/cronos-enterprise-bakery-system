package com.ninsky.cronos.kitchen.allergen;

import com.ninsky.cronos.infrastructure.exception.Violations;
import com.ninsky.cronos.kitchen.shared.TextNormalizer;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Field rules of §3.2 for {@link AllergenRequest}. */
final class AllergenRules {

    static final Pattern CODE = Pattern.compile("^[A-Z][A-Z0-9_]{1,49}$");
    static final Set<String> ICONS = Set.of("pi pi-exclamation-triangle", "pi pi-ban", "pi pi-shield", "pi pi-heart", "pi pi-sun",
            "pi pi-bolt", "pi pi-circle", "pi pi-circle-fill", "pi pi-star", "pi pi-flag", "pi pi-tag");
    static final Set<String> REGULATIONS = Set.of("NOM-051", "EU-1169", "FDA-FALCPA", "CODEX");
    static final Set<String> STOP_WORDS = Set.of("de", "la", "con", "y", "sin", "para", "the", "of", "and");
    static final int MAX_KEYWORDS = 60;
    static final int MAX_KEYWORD_LENGTH = 40;

    private AllergenRules() {
    }

    /** Normalised, de-duplicated keywords in request order. */
    static List<String> keywords(List<String> raw) {
        Set<String> normalized = new LinkedHashSet<>();
        raw.stream().filter(Objects::nonNull).map(TextNormalizer::normalize).forEach(normalized::add);
        return List.copyOf(normalized);
    }

    static void check(AllergenRequest request, boolean creating, Violations violations) {
        if (creating) {
            violations.invalidIf(request.code() == null || !CODE.matcher(request.code()).matches(), "code", "api.validation.pattern");
        }
        String name = request.name() == null ? "" : request.name().strip();
        violations.invalidIf(name.isEmpty() || name.length() > 80, "name", "api.validation.length", 1, 80);
        violations.invalidIf(request.description() != null && request.description().length() > 500, "description",
                "api.validation.maxLength", 500);
        violations.invalidIf(request.icon() == null || !ICONS.contains(request.icon()), "icon", "kitchen.allergen.icon.invalid");
        checkKeywords(request.keywords(), violations);
        violations.invalidIf(!REGULATIONS.containsAll(request.regulations()), "regulations", "kitchen.allergen.regulations.invalid");
    }

    static void checkKeywords(List<String> raw, Violations violations) {
        List<String> keywords = keywords(raw);
        violations.invalidIf(keywords.isEmpty() || keywords.size() > MAX_KEYWORDS, "keywords", "api.validation.listSize", 1, MAX_KEYWORDS);
        for (int i = 0; i < raw.size(); i++) {
            String normalized = TextNormalizer.normalize(raw.get(i));
            String field = "keywords[" + i + "]";
            if (normalized.isEmpty() || normalized.length() > MAX_KEYWORD_LENGTH) {
                violations.invalid(field, "api.validation.length", 1, MAX_KEYWORD_LENGTH);
            } else if (STOP_WORDS.contains(normalized)) {
                violations.invalid(field, "kitchen.allergen.keyword.stopWord", normalized);
            }
        }
    }
}
