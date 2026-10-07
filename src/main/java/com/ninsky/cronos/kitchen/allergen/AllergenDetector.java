package com.ninsky.cronos.kitchen.allergen;

import com.ninsky.cronos.kitchen.shared.TextNormalizer;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Whole-word keyword matcher (§3.3). Immutable and thread-safe: build once per keyword set
 * (cached per tenant) and reuse. Reports the longest matching keyword per allergen.
 */
public final class AllergenDetector {

    /** One searchable keyword, already normalised. */
    public record Keyword(long allergenId, String code, String name, String keyword) {
    }

    /** A suggestion: the allergen and the keyword that matched. */
    public record Match(long allergenId, String code, String name, String keyword) {
    }

    private record Compiled(Keyword keyword, String padded) {
    }

    /** Per allergen, longest keyword first so the first hit is the one to report. */
    private final Map<Long, List<Compiled>> byAllergen;

    private AllergenDetector(Map<Long, List<Compiled>> byAllergen) {
        this.byAllergen = byAllergen;
    }

    public static AllergenDetector of(Collection<Keyword> keywords) {
        Map<Long, List<Compiled>> grouped = keywords.stream()
                .map(k -> new Keyword(k.allergenId(), k.code(), k.name(), TextNormalizer.normalize(k.keyword())))
                .filter(k -> !k.keyword().isEmpty())
                .distinct()
                .map(k -> new Compiled(k, ' ' + k.keyword() + ' '))
                .collect(Collectors.groupingBy(c -> c.keyword().allergenId(), Collectors.collectingAndThen(Collectors.toList(),
                        list -> list.stream().sorted(Comparator.comparingInt((Compiled c) -> c.keyword().keyword().length()).reversed()).toList())));
        return new AllergenDetector(Map.copyOf(grouped));
    }

    public static AllergenDetector empty() {
        return new AllergenDetector(Map.of());
    }

    /** Suggestions for {@code text}, sorted by allergen name; {@code excludeIds} are skipped. */
    public List<Match> detect(String text, Set<Long> excludeIds) {
        String padded = ' ' + TextNormalizer.normalize(text) + ' ';
        if (padded.isBlank()) {
            return List.of();
        }
        Set<Long> excluded = excludeIds == null ? Set.of() : excludeIds;
        return byAllergen.entrySet().stream()
                .filter(e -> !excluded.contains(e.getKey()))
                .map(e -> firstHit(e.getValue(), padded))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(Match::name, String.CASE_INSENSITIVE_ORDER).thenComparing(Match::allergenId))
                .toList();
    }

    /** Detection over several texts at once (name + description, line notes…). */
    public List<Match> detect(Collection<String> texts, Set<Long> excludeIds) {
        return detect(texts.stream().filter(t -> t != null && !t.isBlank()).collect(Collectors.joining(" . ")), excludeIds);
    }

    private static Optional<Match> firstHit(List<Compiled> keywords, String padded) {
        return keywords.stream()
                .filter(c -> padded.contains(c.padded()))
                .findFirst()
                .map(Compiled::keyword)
                .map(k -> new Match(k.allergenId(), k.code(), k.name(), k.keyword()));
    }

    public Set<Long> allergenIds() {
        return byAllergen.keySet();
    }
}
