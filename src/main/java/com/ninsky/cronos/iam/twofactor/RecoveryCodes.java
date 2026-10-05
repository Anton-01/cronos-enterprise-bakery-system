package com.ninsky.cronos.iam.twofactor;

import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.IntStream;

/** One-time recovery codes {@code XXXX-XXXX} over the Crockford Base32 alphabet (no I, L, O, U). */
public final class RecoveryCodes {

    public static final int COUNT = 10;
    private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    private RecoveryCodes() {
    }

    public static List<String> generate() {
        return IntStream.range(0, COUNT).mapToObj(i -> generateOne()).toList();
    }

    /** Canonical {@code XXXX-XXXX} form of user input (case, spaces and Crockford look-alikes forgiven). */
    public static Optional<String> normalize(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String compact = input.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT)
                .replace('O', '0').replace('I', '1').replace('L', '1');
        if (compact.length() != 8 || !compact.chars().allMatch(c -> ALPHABET.indexOf(c) >= 0)) {
            return Optional.empty();
        }
        return Optional.of(compact.substring(0, 4) + "-" + compact.substring(4));
    }

    /** A 6-digit string is a TOTP; anything else is treated as a recovery code. */
    public static boolean looksLikeTotp(String input) {
        return input != null && input.trim().matches("\\d{" + Totp.DIGITS + "}");
    }

    private static String generateOne() {
        StringBuilder code = new StringBuilder(9);
        IntStream.range(0, 8).forEach(i -> {
            if (i == 4) {
                code.append('-');
            }
            code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        });
        return code.toString();
    }
}
