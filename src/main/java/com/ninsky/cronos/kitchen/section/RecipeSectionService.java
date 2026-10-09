package com.ninsky.cronos.kitchen.section;

import com.ninsky.cronos.iam.shared.ActorProvider;
import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.infrastructure.exception.Violations;
import com.ninsky.cronos.kitchen.shared.KitchenSettingsCustomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The user's section labels (baking-studio §3). Labels only feed the picker: recipe lines keep {@code section}
 * as free text, so renaming or deleting a label never rewrites a recipe (B3). Single owner, last write wins;
 * no audit (low-risk personal labels).
 */
@Service
@RequiredArgsConstructor
public class RecipeSectionService {

    static final int MAX_SECTIONS = 60;
    static final int MAX_NAME = 40;
    private static final Pattern COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private final RecipeSectionCustomRepository sections;
    private final KitchenSettingsCustomRepository settings;
    private final ActorProvider actors;
    private final Clock clock;

    /** Ordered by {@code displayOrder, name}; the first call of a user seeds the defaults and the sections already in use. */
    @Transactional
    public List<RecipeSection> list() {
        UUID owner = owner();
        ensureSeeded(owner);
        return sections.list(owner);
    }

    @Transactional
    public RecipeSection create(RecipeSectionRequest request) {
        UUID owner = owner();
        ensureSeeded(owner);
        Valid valid = validate(owner, request, null);
        if (sections.count(owner) >= MAX_SECTIONS) {
            throw ApiException.of(ApiErrorCode.QUOTA_EXCEEDED, null, "kitchen.section.quota", MAX_SECTIONS);
        }
        UUID id = UUID.randomUUID();
        try {
            sections.insert(owner, id, valid.name(), valid.color(), clock.instant());
        } catch (DuplicateKeyException race) {
            throw duplicate();
        }
        return sections.find(owner, id).orElseThrow();
    }

    /** Renaming changes the catalog only (B3): the old name's lines simply stop counting for this label. */
    @Transactional
    public RecipeSection update(UUID id, RecipeSectionRequest request) {
        UUID owner = owner();
        sections.find(owner, id).orElseThrow(RecipeSectionService::notFound);
        Valid valid = validate(owner, request, id);
        try {
            sections.update(owner, id, valid.name(), valid.color(), clock.instant());
        } catch (DuplicateKeyException race) {
            throw duplicate();
        }
        return sections.find(owner, id).orElseThrow();
    }

    /** Allowed while lines use the name: they keep their text. */
    @Transactional
    public void delete(UUID id) {
        if (!sections.delete(owner(), id)) {
            throw notFound();
        }
    }

    /** Listed ids take {@code 0..n-1}; the others keep their relative order after them. Unknown or repeated ids → 400. */
    @Transactional
    public List<RecipeSection> reorder(RecipeSectionOrderRequest request) {
        UUID owner = owner();
        List<UUID> current = sections.orderedIds(owner);
        Set<UUID> known = new HashSet<>(current);
        Set<UUID> listed = new LinkedHashSet<>();
        Violations violations = new Violations();
        for (int i = 0; i < request.ids().size(); i++) {
            UUID id = request.ids().get(i);
            if (id == null || !known.contains(id)) {
                violations.invalid("ids[" + i + "]", "kitchen.section.unknown");
            } else if (!listed.add(id)) {
                violations.invalid("ids[" + i + "]", "api.validation.duplicateEntry");
            }
        }
        violations.throwIfAny();
        List<UUID> order = new ArrayList<>(listed);
        current.stream().filter(id -> !listed.contains(id)).forEach(order::add);
        sections.reorder(owner, order, clock.instant());
        return sections.list(owner);
    }

    /** Re-creates any default whose key is missing; never renames or deletes. */
    @Transactional
    public List<RecipeSection> restoreDefaults() {
        UUID owner = owner();
        ensureSeeded(owner);
        sections.insertMissingDefaults(owner, RecipeSectionDefaults.ALL, MAX_SECTIONS - sections.count(owner), clock.instant());
        return sections.list(owner);
    }

    /** One-time lazy seeding under the settings row lock (two tabs never double-seed; deletions are never undone, B4). */
    private void ensureSeeded(UUID owner) {
        if (settings.peek(owner).sections() || settings.lock(owner).sections()) {
            return;
        }
        Instant now = clock.instant();
        sections.insertMissingDefaults(owner, RecipeSectionDefaults.ALL, MAX_SECTIONS, now);
        sections.insertUsedSections(owner, MAX_SECTIONS - sections.count(owner), now);
        settings.markSectionsSeeded(owner, now);
    }

    private record Valid(String name, String color) {
    }

    /** §3.2: trimmed, whitespace collapsed, 1–40 chars, unique per owner under the section key; colour #RRGGBB or null. */
    private Valid validate(UUID owner, RecipeSectionRequest request, UUID excludeId) {
        Violations violations = new Violations();
        String name = normalize(request == null ? null : request.name());
        String color = request == null || request.color() == null || request.color().isBlank() ? null : request.color().strip();
        if (name == null || name.length() > MAX_NAME) {
            violations.invalid("name", "api.validation.length", 1, MAX_NAME);
        } else if (sections.keyTaken(owner, name, excludeId)) {
            violations.add(ApiErrorCode.DUPLICATE_RESOURCE, "name", "kitchen.section.duplicate", name);
        }
        violations.invalidIf(color != null && !COLOR.matcher(color).matches(), "color", "kitchen.section.color");
        violations.throwIfAny();
        return new Valid(name, color == null ? null : color.toLowerCase(java.util.Locale.ROOT));
    }

    static String normalize(String name) {
        if (name == null) {
            return null;
        }
        String collapsed = SPACES.matcher(name.strip()).replaceAll(" ");
        return collapsed.isEmpty() ? null : collapsed;
    }

    private static ApiException duplicate() {
        return ApiException.of(ApiErrorCode.DUPLICATE_RESOURCE, "name", "kitchen.section.duplicate", "");
    }

    private static ApiException notFound() {
        return ApiException.notFound("kitchen.section.notFound");
    }

    private UUID owner() {
        return actors.require().id();
    }
}
