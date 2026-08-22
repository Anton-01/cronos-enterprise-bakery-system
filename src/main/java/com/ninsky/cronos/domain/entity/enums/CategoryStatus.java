package com.ninsky.cronos.domain.entity.enums;

/**
 * Category-specific lifecycle — deliberately not the shared {@link RecordStatus}
 * (ACTIVE/INACTIVE/ARCHIVED): a category has exactly one non-active state, TRASHED, reached only
 * via soft-delete ({@code CategoryJpaEntity}'s {@code @SQLDelete}), never a manual status PATCH.
 */
public enum CategoryStatus {
    ACTIVE, TRASHED
}
