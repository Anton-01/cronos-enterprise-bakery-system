package com.ninsky.cronos.account.shared.api;

import com.ninsky.cronos.account.shared.domain.ExpectedVersion;

/** Strong ETags carry the entity's optimistic-lock version: {@code "7"}. */
public final class ETags {

    private ETags() {
    }

    public static String of(long version) {
        return "\"" + version + "\"";
    }

    /**
     * {@code null} / blank / {@code *} → no precondition. A weak or quoted tag is unwrapped; a value
     * that is not one of our versions can never match, so it yields a precondition that fails (412).
     */
    public static ExpectedVersion parseIfMatch(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank() || ifMatch.strip().equals("*")) {
            return ExpectedVersion.ANY;
        }
        String tag = ifMatch.strip();
        if (tag.startsWith("W/")) {
            tag = tag.substring(2);
        }
        if (tag.length() >= 2 && tag.startsWith("\"") && tag.endsWith("\"")) {
            tag = tag.substring(1, tag.length() - 1);
        }
        try {
            return ExpectedVersion.of(Long.parseLong(tag));
        } catch (NumberFormatException e) {
            return ExpectedVersion.of(Long.MIN_VALUE);
        }
    }
}
