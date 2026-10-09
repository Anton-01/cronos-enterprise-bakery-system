package com.ninsky.cronos.iam.permission;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.ninsky.cronos.iam.permission.PermissionRisk.CRITICAL;
import static com.ninsky.cronos.iam.permission.PermissionRisk.HIGH;
import static com.ninsky.cronos.iam.permission.PermissionRisk.LOW;
import static com.ninsky.cronos.iam.permission.PermissionRisk.MEDIUM;
import static com.ninsky.cronos.iam.permission.Permissions.*;

/**
 * Single source of truth for the permission catalog (spec §5.2). Synchronised to the
 * {@code permissions} table at startup; never creatable through the API.
 */
public final class PermissionCatalog {

    private static final List<PermissionDefinition> DEFINITIONS = Stream.of(
            PermissionDefinition.of(DASHBOARD_HOME_READ, LOW),

            PermissionDefinition.of(RECIPE_READ, LOW),
            PermissionDefinition.of(RECIPE_CREATE, LOW),
            PermissionDefinition.of(RECIPE_UPDATE, LOW),
            PermissionDefinition.of(RECIPE_DELETE, MEDIUM),
            PermissionDefinition.of(RECIPE_SHARE, MEDIUM),

            PermissionDefinition.of(QUOTE_READ, LOW),
            PermissionDefinition.of(QUOTE_CREATE, LOW),
            PermissionDefinition.of(QUOTE_UPDATE, LOW),
            PermissionDefinition.of(QUOTE_DELETE, MEDIUM),
            PermissionDefinition.of(QUOTE_SHARE, MEDIUM),
            PermissionDefinition.of(QUOTE_APPROVE, HIGH),

            PermissionDefinition.of(INGREDIENT_READ, LOW),
            PermissionDefinition.of(INGREDIENT_CREATE, LOW),
            PermissionDefinition.of(INGREDIENT_UPDATE, LOW),
            PermissionDefinition.of(INGREDIENT_DELETE, MEDIUM),

            PermissionDefinition.of(FIXED_COST_READ, LOW),
            PermissionDefinition.of(FIXED_COST_MANAGE, MEDIUM),

            PermissionDefinition.of(GUIDE_GUIDE_READ, LOW),
            PermissionDefinition.standalone(GUIDE_PAN_MANAGE, LOW, GUIDE_GUIDE_READ),
            PermissionDefinition.standalone(GUIDE_CONTENT_MANAGE, MEDIUM, GUIDE_GUIDE_READ),

            PermissionDefinition.of(CATALOG_UNIT_TYPE_READ, LOW),
            PermissionDefinition.of(CATALOG_UNIT_TYPE_MANAGE, MEDIUM),
            PermissionDefinition.of(CATALOG_MEASUREMENT_UNIT_READ, LOW),
            PermissionDefinition.of(CATALOG_MEASUREMENT_UNIT_MANAGE, MEDIUM),
            PermissionDefinition.of(CATALOG_CATEGORY_READ, LOW),
            PermissionDefinition.of(CATALOG_CATEGORY_MANAGE, MEDIUM),
            PermissionDefinition.of(CATALOG_ALLERGEN_READ, LOW),
            PermissionDefinition.of(CATALOG_ALLERGEN_MANAGE, MEDIUM),
            PermissionDefinition.of(CATALOG_INGREDIENT_READ, LOW),
            PermissionDefinition.of(CATALOG_INGREDIENT_MANAGE, HIGH, INGREDIENT_READ),
            PermissionDefinition.of(CATALOG_IMPORT_READ, MEDIUM),
            PermissionDefinition.of(CATALOG_IMPORT_EXECUTE, HIGH),

            PermissionDefinition.of(FINANCE_CURRENCY_READ, LOW),
            PermissionDefinition.of(FINANCE_CURRENCY_MANAGE, MEDIUM),
            PermissionDefinition.of(FINANCE_TAX_RATE_READ, LOW),
            PermissionDefinition.of(FINANCE_TAX_RATE_MANAGE, HIGH),
            PermissionDefinition.standalone(FINANCE_SETTINGS_UPDATE, HIGH, FINANCE_CURRENCY_READ, FINANCE_TAX_RATE_READ),

            PermissionDefinition.of(IAM_USER_READ, MEDIUM),
            PermissionDefinition.of(IAM_USER_CREATE, HIGH),
            PermissionDefinition.of(IAM_USER_UPDATE, MEDIUM),
            PermissionDefinition.of(IAM_USER_CHANGE_STATUS, HIGH),
            PermissionDefinition.of(IAM_USER_RESET_CREDENTIALS, HIGH),
            PermissionDefinition.of(IAM_USER_MANAGE_ACCESS, CRITICAL, IAM_ROLE_READ, IAM_PERMISSION_GROUP_READ),
            PermissionDefinition.of(IAM_USER_MANAGE_SESSIONS, HIGH),
            PermissionDefinition.of(IAM_USER_EXPORT, MEDIUM),

            PermissionDefinition.of(IAM_ROLE_READ, MEDIUM),
            PermissionDefinition.of(IAM_ROLE_CREATE, HIGH),
            PermissionDefinition.of(IAM_ROLE_UPDATE, CRITICAL),
            PermissionDefinition.of(IAM_ROLE_DELETE, HIGH),
            PermissionDefinition.of(IAM_ROLE_MANAGE_MEMBERS, CRITICAL, IAM_USER_READ),

            PermissionDefinition.of(IAM_PERMISSION_GROUP_READ, MEDIUM),
            PermissionDefinition.of(IAM_PERMISSION_GROUP_CREATE, HIGH),
            PermissionDefinition.of(IAM_PERMISSION_GROUP_UPDATE, CRITICAL),
            PermissionDefinition.of(IAM_PERMISSION_GROUP_DELETE, HIGH),

            PermissionDefinition.of(IAM_AUDIT_READ, MEDIUM),
            PermissionDefinition.of(IAM_AUDIT_EXPORT, MEDIUM),

            PermissionDefinition.of(IAM_SECURITY_POLICY_READ, MEDIUM),
            PermissionDefinition.of(IAM_SECURITY_POLICY_UPDATE, CRITICAL)
    ).sorted(PermissionDefinition.CANONICAL_ORDER).toList();

    private static final Map<String, PermissionDefinition> BY_CODE = DEFINITIONS.stream()
            .collect(Collectors.toMap(PermissionDefinition::code, Function.identity(), (a, b) -> {
                throw new IllegalStateException("Duplicate permission code " + a.code());
            }, LinkedHashMap::new));

    static {
        validate();
    }

    private PermissionCatalog() {
    }

    /** Every definition, in canonical order. */
    public static List<PermissionDefinition> all() {
        return DEFINITIONS;
    }

    public static Set<String> codes() {
        return BY_CODE.keySet();
    }

    public static Optional<PermissionDefinition> find(String code) {
        return Optional.ofNullable(BY_CODE.get(code));
    }

    public static boolean contains(String code) {
        return BY_CODE.containsKey(code);
    }

    /** Codes of one module/resource, e.g. {@code ("CATALOG", null)} for the whole module. */
    public static Set<String> matching(String module, String resource, String action) {
        return DEFINITIONS.stream()
                .filter(d -> module == null || d.module().equals(module))
                .filter(d -> resource == null || d.resource().equals(resource))
                .filter(d -> action == null || d.action().equals(action))
                .map(PermissionDefinition::code)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /** {@code codes} plus everything they transitively depend on (what a role stores, §4.2). */
    public static SortedSet<String> withDependencies(Collection<String> codes) {
        SortedSet<String> closed = new TreeSet<>();
        Deque<String> pending = new ArrayDeque<>(codes);
        while (!pending.isEmpty()) {
            String code = pending.pop();
            if (closed.add(code)) {
                find(code).map(PermissionDefinition::dependsOn).ifPresent(pending::addAll);
            }
        }
        return closed;
    }

    /**
     * Drops every code whose dependencies are not all present, repeating until stable
     * (removing one code can orphan another).
     */
    public static SortedSet<String> dropOrphans(Collection<String> codes) {
        SortedSet<String> kept = new TreeSet<>(codes);
        boolean changed;
        do {
            changed = kept.removeIf(code -> find(code).map(d -> !kept.containsAll(d.dependsOn())).orElse(true));
        } while (changed);
        return kept;
    }

    /** Fails fast when a dependency is unknown or the graph has a cycle. */
    static void validate() {
        Map<String, Set<String>> graph = new HashMap<>();
        DEFINITIONS.forEach(d -> {
            d.dependsOn().stream().filter(dep -> !BY_CODE.containsKey(dep)).findFirst().ifPresent(dep -> {
                throw new IllegalStateException(d.code() + " depends on unknown permission " + dep);
            });
            graph.put(d.code(), d.dependsOn());
        });
        Set<String> done = new HashSet<>();
        graph.keySet().forEach(code -> visit(code, graph, new HashSet<>(), done));
    }

    private static void visit(String code, Map<String, Set<String>> graph, Set<String> path, Set<String> done) {
        if (done.contains(code)) {
            return;
        }
        if (!path.add(code)) {
            throw new IllegalStateException("Permission dependency cycle through " + code);
        }
        graph.getOrDefault(code, Set.of()).forEach(dep -> visit(dep, graph, path, done));
        path.remove(code);
        done.add(code);
    }
}
