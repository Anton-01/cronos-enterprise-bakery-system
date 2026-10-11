package com.ninsky.cronos.quote.domain;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/** Quote numbers and public link tokens. */
public final class QuoteNumbers {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 21;

    private QuoteNumbers() {
    }

    /** {@code CR-{first 4 of the owner id}-{yyyyMMddHHmmss}} (seconds avoid clashes when duplicating twice a minute). */
    public static String next(UUID ownerId, LocalDateTime now) {
        return "CR-" + ownerId.toString().substring(0, 4).toUpperCase(Locale.ROOT) + "-" + now.format(STAMP);
    }

    /** 42 hex characters (168 random bits) for the public link. */
    public static String publicToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
