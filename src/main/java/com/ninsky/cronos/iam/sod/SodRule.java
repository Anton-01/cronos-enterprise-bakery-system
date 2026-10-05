package com.ninsky.cronos.iam.sod;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Conflict when the subject holds at least one code from every set. */
public record SodRule(String code, String nameEs, String nameEn, String descriptionEs, String descriptionEn,
                      SodSeverity severity, List<Set<String>> permissionSets) {

    public SodRule {
        permissionSets = permissionSets.stream().map(Set::copyOf).toList();
    }

    public String name(Locale locale) {
        return isEnglish(locale) ? nameEn : nameEs;
    }

    public String description(Locale locale) {
        return isEnglish(locale) ? descriptionEn : descriptionEs;
    }

    private static boolean isEnglish(Locale locale) {
        return locale != null && "en".equals(locale.getLanguage());
    }
}
