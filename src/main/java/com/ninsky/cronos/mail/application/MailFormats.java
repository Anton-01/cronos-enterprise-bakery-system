package com.ninsky.cronos.mail.application;

import com.ninsky.cronos.mail.domain.MailLocales;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

/** Values rendered into email variables, in the reader's locale. */
public final class MailFormats {

    private MailFormats() {
    }

    /** {@code $2,450.00 MXN}; empty for no amount. */
    public static String money(BigDecimal amount, String currency, String locale) {
        if (amount == null) {
            return "";
        }
        NumberFormat format = NumberFormat.getNumberInstance(MailLocales.toLocale(locale));
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return "$" + format.format(amount) + " " + (currency == null ? "" : currency.toUpperCase(Locale.ROOT));
    }

    /** Long date ({@code 14 de octubre de 2026}); empty for no date. */
    public static String date(LocalDate date, String locale) {
        return date == null ? "" : DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(MailLocales.toLocale(locale)).format(date);
    }

    /** Long date and short time in {@code zone}, or only the date for an all-day entry. */
    public static String dateTime(Instant instant, boolean allDay, ZoneId zone, String locale) {
        if (instant == null) {
            return "";
        }
        if (allDay) {
            return date(instant.atZone(zone).toLocalDate(), locale);
        }
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG, FormatStyle.SHORT).withLocale(MailLocales.toLocale(locale))
                .format(instant.atZone(zone));
    }
}
