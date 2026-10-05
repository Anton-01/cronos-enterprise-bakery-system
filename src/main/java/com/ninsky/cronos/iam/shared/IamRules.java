package com.ninsky.cronos.iam.shared;

import com.ninsky.cronos.iam.permission.PermissionCatalog;
import com.ninsky.cronos.infrastructure.exception.Violations;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Field rules reused by users, roles and groups. Each method adds its violations and returns the cleaned value. */
public final class IamRules {

    public static final Pattern CODE = Pattern.compile("^[A-Z][A-Z0-9_]{1,49}$");
    public static final Pattern COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    public static final int REASON_MIN = 10;
    public static final int REASON_MAX = 500;

    private IamRules() {
    }

    /** Justification of privileged changes: required, 10–500 after trimming. */
    public static String reason(Violations violations, String field, String raw) {
        String reason = Texts.clean(raw);
        if (reason == null || Texts.length(reason) < REASON_MIN || Texts.length(reason) > REASON_MAX) {
            violations.invalid(field, "api.validation.reasonLength");
        }
        return reason;
    }

    public static String code(Violations violations, String field, String raw) {
        String code = Texts.clean(raw);
        if (code == null) {
            violations.invalid(field, "api.validation.required");
        } else if (!CODE.matcher(code).matches() || code.startsWith("ROLE_")) {
            violations.invalid(field, "iam.validation.code");
        }
        return code;
    }

    public static String requiredText(Violations violations, String field, String raw, int max) {
        String value = Texts.collapse(raw);
        if (value == null) {
            violations.invalid(field, "api.validation.required");
        } else if (Texts.length(value) > max) {
            violations.invalid(field, "api.validation.maxLength", max);
        }
        return value;
    }

    public static String optionalText(Violations violations, String field, String raw, int max) {
        String value = Texts.clean(raw);
        if (value != null && Texts.length(value) > max) {
            violations.invalid(field, "api.validation.maxLength", max);
        }
        return value;
    }

    public static String color(Violations violations, String field, String raw) {
        String color = Texts.clean(raw);
        if (color != null && !COLOR.matcher(color).matches()) {
            violations.invalid(field, "iam.validation.color");
        }
        return color;
    }

    /** Catalog codes: each known, no duplicates; errors point at {@code field[i]}. */
    public static List<String> permissionCodes(Violations violations, String field, Collection<String> raw) {
        List<String> codes = raw == null ? List.of() : List.copyOf(raw);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < codes.size(); i++) {
            String code = codes.get(i);
            if (code == null || !PermissionCatalog.contains(code)) {
                violations.invalid(field + "[" + i + "]", "api.validation.unknownPermission", code);
            } else if (!seen.add(code)) {
                violations.invalid(field + "[" + i + "]", "api.validation.duplicateEntry");
            }
        }
        return codes.stream().filter(PermissionCatalog::contains).distinct().toList();
    }

    /** Ids without duplicates; errors point at {@code field[i]}. */
    public static <T> List<T> distinctIds(Violations violations, String field, Collection<T> raw) {
        List<T> ids = raw == null ? List.of() : List.copyOf(raw);
        Set<T> seen = new HashSet<>();
        for (int i = 0; i < ids.size(); i++) {
            if (ids.get(i) == null) {
                violations.invalid(field + "[" + i + "]", "api.validation.required");
            } else if (!seen.add(ids.get(i))) {
                violations.invalid(field + "[" + i + "]", "api.validation.duplicateEntry");
            }
        }
        return ids.stream().filter(Objects::nonNull).distinct().toList();
    }
}
