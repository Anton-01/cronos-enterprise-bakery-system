package com.ninsky.cronos.notification.application;

import com.ninsky.cronos.notification.domain.NotificationType;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders {@code notification.{TYPE}.title|body} in the recipient's language with named arguments ({@code {quoteNumber}}),
 * once, at creation: the inbox keeps the text it showed even when the copy changes later.
 */
@Component
public class NotificationTexts {

    static final int TITLE_MAX = 200;
    static final int BODY_MAX = 1000;
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z][a-zA-Z0-9]*)}");

    /** Rendered title and body. */
    public record Rendered(String title, String body) {
    }

    private final MessageSource messages;

    public NotificationTexts(MessageSource messages) {
        this.messages = messages;
    }

    public Rendered render(NotificationType type, Map<String, ?> args, Locale locale, ZoneId zone) {
        String prefix = "notification." + type.name() + ".";
        return new Rendered(cut(fill(messages.getMessage(prefix + "title", null, type.name(), locale), args, locale, zone), TITLE_MAX),
                cut(fill(messages.getMessage(prefix + "body", null, "", locale), args, locale, zone), BODY_MAX));
    }

    /** Replaces every {@code {name}} with its formatted argument (unknown names are left as they are). */
    static String fill(String pattern, Map<String, ?> args, Locale locale, ZoneId zone) {
        Matcher matcher = PLACEHOLDER.matcher(pattern == null ? "" : pattern);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = args.containsKey(name) ? format(args.get(name), locale, zone) : matcher.group();
            matcher.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    static String format(Object value, Locale locale, ZoneId zone) {
        return switch (value) {
            case null -> "";
            case Instant instant -> DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale)
                    .format(instant.atZone(zone));
            case LocalDate date -> DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date);
            case BigDecimal amount -> amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
            default -> value.toString();
        };
    }

    private static String cut(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
