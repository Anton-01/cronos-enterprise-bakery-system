package com.ninsky.cronos.iam.sod;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Pure SoD check of an effective permission set against the configured rules. */
public final class SodEvaluator {

    private SodEvaluator() {
    }

    public static List<SodConflict> evaluate(Collection<SodRule> rules, Set<String> effective, Locale locale) {
        return rules.stream()
                .filter(rule -> !rule.permissionSets().isEmpty())
                .filter(rule -> rule.permissionSets().stream().allMatch(set -> set.stream().anyMatch(effective::contains)))
                .map(rule -> new SodConflict(rule.code(), rule.name(locale), rule.description(locale), rule.severity(),
                        rule.permissionSets().stream().flatMap(Set::stream).filter(effective::contains).sorted().distinct().toList()))
                .toList();
    }
}
