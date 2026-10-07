package com.ninsky.cronos.kitchen.shared;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Detection normalisation (§3.3), identical to the UI's {@code allergen-detection.ts}:
 * lower(stripAccents(s)) → [^a-z0-9ñ ]+ → ' ' → collapse spaces → trim. ñ survives.
 */
public final class TextNormalizer {

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_WORD = Pattern.compile("[^a-z0-9ñ ]+");
    private static final Pattern SPACES = Pattern.compile(" {2,}");
    private static final char ENYE_PLACEHOLDER = '\u0001';

    private TextNormalizer() {
    }

    public static String normalize(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String lower = value.toLowerCase(Locale.ROOT).replace('ñ', ENYE_PLACEHOLDER);
        String stripped = MARKS.matcher(Normalizer.normalize(lower, Normalizer.Form.NFD)).replaceAll("")
                .replace(ENYE_PLACEHOLDER, 'ñ');
        String words = NON_WORD.matcher(stripped).replaceAll(" ");
        return SPACES.matcher(words).replaceAll(" ").strip();
    }

    /** Case- and accent-insensitive key for uniqueness checks (keeps punctuation). */
    public static String fold(String value) {
        if (value == null) {
            return "";
        }
        return MARKS.matcher(Normalizer.normalize(value.strip().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)).replaceAll("");
    }
}
