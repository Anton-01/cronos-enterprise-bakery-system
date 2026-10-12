package com.ninsky.cronos.identity.domain;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Self-registration username rules (§2.4.1): shape after lower-casing and the reserved names. */
public final class UsernameRules {

    private static final Pattern SHAPE = Pattern.compile("^[a-z][a-z0-9._-]{2,31}$");
    private static final Set<String> RESERVED = Set.of("admin", "administrator", "root", "support", "soporte", "cronos", "system",
            "sistema", "api", "help", "ayuda", "security", "seguridad", "postmaster", "webmaster", "no-reply", "noreply", "null",
            "undefined", "superadmin", "super_admin", "owner", "staff");

    private UsernameRules() {
    }

    /** Lower-cased (ROOT) and trimmed. */
    public static String normalize(String username) {
        return username == null ? null : username.trim().toLowerCase(Locale.ROOT);
    }

    /** True when {@code normalized} has the allowed shape. */
    public static boolean wellFormed(String normalized) {
        return normalized != null && SHAPE.matcher(normalized).matches();
    }

    /** True for names nobody can register. */
    public static boolean reserved(String normalized) {
        return normalized != null && RESERVED.contains(normalized);
    }
}
