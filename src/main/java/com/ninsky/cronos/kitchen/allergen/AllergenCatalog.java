package com.ninsky.cronos.kitchen.allergen;

import com.ninsky.cronos.kitchen.shared.AllergenRef;
import com.ninsky.cronos.kitchen.shared.KitchenCaches;
import com.ninsky.cronos.kitchen.shared.KitchenStatus;
import com.ninsky.cronos.kitchen.shared.Scope;
import com.ninsky.cronos.kitchen.shared.Sql;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Allergens visible to a tenant (SYSTEM ∪ own) with names, keywords and the compiled detector,
 * cached per tenant in {@code kitchenAllergens}. Every allergen write evicts the affected tenant;
 * platform-wide changes clear the cache.
 */
@Component
@RequiredArgsConstructor
public class AllergenCatalog {

    private final NamedParameterJdbcTemplate jdbc;
    private final KitchenCaches caches;

    /** One allergen with every locale; keyword maps are locale → normalised keywords. */
    public record Entry(long id, String code, UUID ownerId, String icon, List<String> regulations, KitchenStatus status,
                        Map<String, String> names, Map<String, String> descriptions,
                        Map<String, List<String>> platformKeywords, Map<String, List<String>> tenantKeywords,
                        UUID legacyId, long version, Instant updatedAt) {

        public Scope scope() {
            return Scope.of(ownerId);
        }

        public boolean active() {
            return status == KitchenStatus.ACTIVE;
        }

        public String name(String language) {
            return localized(names, language);
        }

        public String description(String language) {
            return localized(descriptions, language);
        }

        /** Platform keywords of {@code language} plus every keyword the tenant added. */
        public List<String> keywords(String language) {
            Set<String> merged = new LinkedHashSet<>(platformKeywords.getOrDefault(language, List.of()));
            tenantKeywords.values().forEach(merged::addAll);
            return List.copyOf(merged);
        }

        public Set<String> allPlatformKeywords() {
            return platformKeywords.values().stream().flatMap(List::stream).collect(Collectors.toUnmodifiableSet());
        }

        public Set<String> allTenantKeywords() {
            return tenantKeywords.values().stream().flatMap(List::stream).collect(Collectors.toUnmodifiableSet());
        }

        public AllergenRef ref(String language) {
            return new AllergenRef(id, code, name(language), icon);
        }

        private static String localized(Map<String, String> values, String language) {
            return Optional.ofNullable(values.get(language)).or(() -> Optional.ofNullable(values.get("es")))
                    .orElseGet(() -> values.values().stream().findFirst().orElse(null));
        }
    }

    /** A tenant's view: entries by id (name order) and the detector over ACTIVE allergens, both locales. */
    public record View(Map<Long, Entry> byId, AllergenDetector detector) {

        public Optional<Entry> find(long id) {
            return Optional.ofNullable(byId.get(id));
        }

        public List<AllergenRef> refs(Collection<Long> ids, String language) {
            return ids.stream().distinct().map(byId::get).filter(Objects::nonNull)
                    .map(e -> e.ref(language))
                    .sorted(Comparator.comparing(AllergenRef::name, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        }
    }

    public View view(UUID tenantId) {
        return caches.get(KitchenCaches.ALLERGENS, tenantId, () -> load(tenantId));
    }

    public void evict(UUID tenantId) {
        caches.evict(KitchenCaches.ALLERGENS, tenantId);
    }

    public void evictAll() {
        caches.clear(KitchenCaches.ALLERGENS);
    }

    private View load(UUID tenantId) {
        Map<String, Object> params = Map.of("tenant", tenantId);
        record Head(long id, String code, UUID ownerId, String icon, List<String> regulations, KitchenStatus status,
                    UUID legacyId, long version, Instant updatedAt) {
        }
        List<Head> heads = jdbc.query("""
                SELECT id, code, owner_id, icon, regulations, status, legacy_id, version, updated_at
                FROM allergens WHERE owner_id IS NULL OR owner_id = :tenant""", params,
                (rs, i) -> new Head(rs.getLong("id"), rs.getString("code"), Sql.uuid(rs, "owner_id"), rs.getString("icon"),
                        Sql.strings(rs, "regulations"), KitchenStatus.valueOf(rs.getString("status")), Sql.uuid(rs, "legacy_id"),
                        rs.getLong("version"), Sql.instant(rs, "updated_at")));

        Map<Long, Map<String, String>> names = new HashMap<>();
        Map<Long, Map<String, String>> descriptions = new HashMap<>();
        jdbc.query("""
                SELECT n.allergen_id, n.locale, n.name, n.description FROM allergen_i18n n
                JOIN allergens a ON a.id = n.allergen_id WHERE a.owner_id IS NULL OR a.owner_id = :tenant""", params, rs -> {
            long id = rs.getLong("allergen_id");
            names.computeIfAbsent(id, k -> new HashMap<>()).put(rs.getString("locale"), rs.getString("name"));
            Optional.ofNullable(rs.getString("description"))
                    .ifPresent(d -> descriptions.computeIfAbsent(id, k -> new HashMap<>()).put(rs.getString("locale"), d));
        });

        Map<Long, Map<String, List<String>>> platform = new HashMap<>();
        Map<Long, Map<String, List<String>>> own = new HashMap<>();
        jdbc.query("""
                SELECT k.allergen_id, k.owner_id, k.locale, k.keyword FROM allergen_keywords k
                JOIN allergens a ON a.id = k.allergen_id
                WHERE (a.owner_id IS NULL OR a.owner_id = :tenant) AND (k.owner_id IS NULL OR k.owner_id = :tenant)
                ORDER BY k.keyword""", params, rs -> {
            Map<Long, Map<String, List<String>>> target = rs.getObject("owner_id") == null ? platform : own;
            target.computeIfAbsent(rs.getLong("allergen_id"), k -> new HashMap<>())
                    .computeIfAbsent(rs.getString("locale"), k -> new java.util.ArrayList<>())
                    .add(rs.getString("keyword"));
        });

        Map<Long, Entry> entries = heads.stream()
                .map(h -> new Entry(h.id(), h.code(), h.ownerId(), h.icon(), h.regulations(), h.status(),
                        Map.copyOf(names.getOrDefault(h.id(), Map.of())), Map.copyOf(descriptions.getOrDefault(h.id(), Map.of())),
                        freeze(platform.get(h.id())), freeze(own.get(h.id())), h.legacyId(), h.version(), h.updatedAt()))
                .sorted(Comparator.comparing((Entry e) -> String.valueOf(e.name("es")), String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toMap(Entry::id, Function.identity(), (a, b) -> a, LinkedHashMap::new));

        AllergenDetector detector = AllergenDetector.of(entries.values().stream()
                .filter(Entry::active)
                .flatMap(e -> java.util.stream.Stream.concat(e.allPlatformKeywords().stream(), e.allTenantKeywords().stream())
                        .map(k -> new AllergenDetector.Keyword(e.id(), e.code(), e.name("es"), k)))
                .toList());
        return new View(java.util.Collections.unmodifiableMap(entries), detector);
    }

    private static Map<String, List<String>> freeze(Map<String, List<String>> values) {
        if (values == null) {
            return Map.of();
        }
        return values.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> List.copyOf(e.getValue())));
    }
}
