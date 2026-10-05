package com.ninsky.cronos.iam.policy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Common/breached password list ({@code security/common-passwords.txt}). Matches the password itself,
 * its leetspeak reading and its core once leading/trailing digits and symbols are removed.
 */
public final class CommonPasswords {

    private static final String RESOURCE = "/security/common-passwords.txt";
    private static final int MIN_CORE = 4;
    private static final Pattern PADDING = Pattern.compile("^[^\\p{L}]+|[^\\p{L}]+$");
    private static final Map<Character, Character> LEET_I = Map.of('@', 'a', '4', 'a', '3', 'e', '1', 'i', '!', 'i',
            '0', 'o', '$', 's', '5', 's', '7', 't');
    private static final Map<Character, Character> LEET_L = Map.of('@', 'a', '4', 'a', '3', 'e', '1', 'l', '!', 'l',
            '0', 'o', '$', 's', '5', 's', '7', 't');

    private final Set<String> entries;

    CommonPasswords(Set<String> entries) {
        this.entries = Set.copyOf(entries);
    }

    public static CommonPasswords load() {
        try (InputStream in = Objects.requireNonNull(CommonPasswords.class.getResourceAsStream(RESOURCE), RESOURCE);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return new CommonPasswords(reader.lines().map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .map(line -> line.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toSet()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public boolean matches(String password) {
        String lower = password.toLowerCase(Locale.ROOT);
        return Stream.of(lower, core(lower))
                .filter(Objects::nonNull)
                .flatMap(candidate -> Stream.of(candidate, translate(candidate, LEET_I), translate(candidate, LEET_L)))
                .flatMap(candidate -> Stream.of(candidate, core(candidate)))
                .filter(Objects::nonNull)
                .anyMatch(entries::contains);
    }

    /** Letters left after stripping digit/symbol padding; null when too short to be meaningful. */
    private static String core(String candidate) {
        String core = PADDING.matcher(candidate).replaceAll("");
        return core.length() >= MIN_CORE ? core : null;
    }

    int size() {
        return entries.size();
    }

    private static String translate(String value, Map<Character, Character> table) {
        return value.chars().mapToObj(c -> String.valueOf(table.getOrDefault((char) c, (char) c))).collect(Collectors.joining());
    }
}
