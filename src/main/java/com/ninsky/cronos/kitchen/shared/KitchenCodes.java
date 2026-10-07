package com.ninsky.cronos.kitchen.shared;

import java.util.Locale;
import java.util.function.Predicate;
import java.util.stream.IntStream;

/** Stable codes from free text, mirroring SQL {@code kitchen_code}: FOO_BAR, starts with a letter. */
public final class KitchenCodes {

    private KitchenCodes() {
    }

    public static String of(String text, String fallback, int maxLength) {
        String code = TextNormalizer.fold(text == null ? "" : text).toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (code.isEmpty()) {
            return fallback;
        }
        if (!Character.isLetter(code.charAt(0))) {
            code = fallback + "_" + code;
        } else if (code.length() < 2) {
            code = code + "_X";
        }
        return code.length() > maxLength ? code.substring(0, maxLength) : code;
    }

    /** {@code base}, else {@code base_2}, {@code base_3}… until {@code taken} says no. */
    public static String unique(String base, int maxLength, Predicate<String> taken) {
        return IntStream.iterate(1, n -> n + 1)
                .mapToObj(n -> n == 1 ? base : suffixed(base, "_" + n, maxLength))
                .filter(taken.negate())
                .findFirst().orElseThrow();
    }

    private static String suffixed(String base, String suffix, int maxLength) {
        return (base.length() + suffix.length() > maxLength ? base.substring(0, maxLength - suffix.length()) : base) + suffix;
    }
}
