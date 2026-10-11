package com.ninsky.cronos.notification.application;

import com.ninsky.cronos.notification.domain.NotificationType;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Named arguments and one rendering per type, in both languages. */
class NotificationTextsTest {

    private static final ZoneId ZONE = ZoneId.of("America/Mexico_City");

    @Test
    void fillsNamedArguments() {
        assertThat(NotificationTexts.fill("{clientName} aceptó {quoteNumber} por ${total}",
                Map.of("clientName", "Ana", "quoteNumber", "CR-AB12", "total", new BigDecimal("2450")), Locale.ROOT, ZONE))
                .isEqualTo("Ana aceptó CR-AB12 por $2450.00");
    }

    @Test
    void leavesUnknownNamesAndEscapesReplacementCharacters() {
        assertThat(NotificationTexts.fill("{a} {b}", Map.of("a", "$1 \\ x"), Locale.ROOT, ZONE)).isEqualTo("$1 \\ x {b}");
    }

    @Test
    void formatsDatesInTheRecipientsZone() {
        assertThat(NotificationTexts.format(Instant.parse("2026-10-20T18:00:00Z"), Locale.forLanguageTag("es-MX"), ZONE)).contains("12:00");
        assertThat(NotificationTexts.format(LocalDate.of(2026, 10, 20), Locale.ENGLISH, ZONE)).contains("2026");
    }

    @Test
    void everyTypeHasATitleInBothLanguages() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasenames("i18n/notification");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        NotificationTexts texts = new NotificationTexts(messages);
        for (Locale locale : new Locale[]{Locale.forLanguageTag("es-MX"), Locale.ENGLISH}) {
            assertThat(Arrays.stream(NotificationType.values()).map(type -> texts.render(type, Map.of(), locale, ZONE).title()))
                    .allMatch(title -> !title.isBlank() && !title.startsWith("notification."));
        }
        assertThat(texts.render(NotificationType.QUOTE_VIEWED, Map.of("clientName", "Ana", "quoteNumber", "CR-1"), Locale.ENGLISH, ZONE).title())
                .isEqualTo("Ana opened quote CR-1");
    }
}
