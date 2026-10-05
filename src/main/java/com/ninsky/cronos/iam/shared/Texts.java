package com.ninsky.cronos.iam.shared;

import java.util.regex.Pattern;

/** Input normalisation shared by IAM requests (spec §3.2, §1.5). */
public final class Texts {

    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}&&[^\\n]]");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private Texts() {
    }

    /** Trimmed, control characters stripped; blank becomes null. */
    public static String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = CONTROL.matcher(value).replaceAll("").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    /** {@link #clean} plus internal whitespace collapsed to one space. */
    public static String collapse(String value) {
        String cleaned = clean(value);
        return cleaned == null ? null : SPACES.matcher(cleaned).replaceAll(" ");
    }

    public static int length(String value) {
        return value == null ? 0 : value.codePointCount(0, value.length());
    }
}
