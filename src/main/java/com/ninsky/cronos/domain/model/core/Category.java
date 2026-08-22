package com.ninsky.cronos.domain.model.core;

import com.ninsky.cronos.domain.entity.enums.CategoryScope;
import com.ninsky.cronos.domain.entity.enums.CategoryStatus;
import com.ninsky.cronos.domain.entity.enums.CategoryType;
import lombok.Builder;

import java.util.Objects;
import java.util.UUID;

/**
 * Pure domain aggregate, deliberately an immutable {@code record} — unlike every other aggregate
 * in {@code domain/model/**} (which are mutable Lombok {@code @Builder} classes, see e.g.
 * {@link com.ninsky.cronos.domain.model.auth.User}). Mutation goes through {@code toBuilder()}
 * (produces a new instance) rather than setters; callers that need to change a field do
 * {@code category.toBuilder().name(newName).build()} instead of {@code category.setName(...)}.
 * {@code version} is carried through (not just a JPA implementation detail) so the optimistic-lock
 * check performed by {@code CategoryJpaEntity}'s {@code @Version} field still works on update.
 */
@Builder(toBuilder = true)
public record Category(
        Long id,
        String name,
        String description,
        CategoryType type,
        CategoryScope scope,
        /** Non-null iff {@code scope == USER}: the sole owner allowed to view/edit/trash this row. */
        UUID userId,
        CategoryStatus status,
        Long version
) {
    /**
     * Defaults {@code status}/{@code version} when unset, e.g. a freshly-built {@code Category}
     * that never specified them. Not Lombok's {@code @Builder.Default} — its {@code = value} header
     * syntax for records didn't survive javac in this build (raw parse error, not an annotation-
     * processing one), so this uses the plain-Java mechanism instead: a compact constructor may
     * reassign a component's parameter before the implicit field assignment.
     */
    public Category {
        if (status == null) {
            status = CategoryStatus.ACTIVE;
        }
        if (version == null) {
            version = 1L;
        }
    }

    public boolean isSystem() {
        return scope == CategoryScope.SYSTEM;
    }

    /** Business rule 3 (IDOR prevention): true only for a USER-scope row owned by exactly this caller. */
    public boolean isOwnedBy(UUID candidateUserId) {
        return scope == CategoryScope.USER && Objects.equals(userId, candidateUserId);
    }
}
