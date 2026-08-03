package com.ninsky.cronos.domain.port;

import java.util.Locale;

/**
 * Pure domain value object — a single bilingual, database-backed error/route catalog entry.
 * Carries both languages; the caller resolves which one to render.
 */
public record ErrorCatalogEntry(
        String errorCode,
        String category,
        int httpStatus,
        String imageUrl,
        String titleEn,
        String titleEs,
        String descriptionEn,
        String descriptionEs
) {
    public String title(Locale locale) {
        return isSpanish(locale) ? titleEs : titleEn;
    }

    public String description(Locale locale) {
        return isSpanish(locale) ? descriptionEs : descriptionEn;
    }

    private static boolean isSpanish(Locale locale) {
        return locale != null && "es".equalsIgnoreCase(locale.getLanguage());
    }
}
